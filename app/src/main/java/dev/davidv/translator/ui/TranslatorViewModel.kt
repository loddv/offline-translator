/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.translator.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.davidv.translator.CatalogRepository
import dev.davidv.translator.DocumentTranslationService
import dev.davidv.translator.DocumentTranslationServiceState
import dev.davidv.translator.FileEvent
import dev.davidv.translator.FilePathManager
import dev.davidv.translator.FromLangChange
import dev.davidv.translator.InputType
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageAvailabilityState
import dev.davidv.translator.LanguageMetadataManager
import dev.davidv.translator.LanguageStateManager
import dev.davidv.translator.LaunchMode
import dev.davidv.translator.MainSelection
import dev.davidv.translator.MainSelectionRules
import dev.davidv.translator.OverlaySource
import dev.davidv.translator.PcmAudioPlayer
import dev.davidv.translator.PdfPhaseProgress
import dev.davidv.translator.PreparedImageOverlay
import dev.davidv.translator.R
import dev.davidv.translator.ReadingOrder
import dev.davidv.translator.Script
import dev.davidv.translator.SettingsManager
import dev.davidv.translator.SpeechError
import dev.davidv.translator.SpeechSynthesisResult
import dev.davidv.translator.TranslatedText
import dev.davidv.translator.TranslationCoordinator
import dev.davidv.translator.TranslationResult
import dev.davidv.translator.TranslatorMessage
import dev.davidv.translator.TxtLayoutChoice
import dev.davidv.translator.WordWithTaggedEntries
import dev.davidv.translator.copyDocumentUriToCache
import dev.davidv.translator.displayNameForUri
import dev.davidv.translator.localizedName
import dev.davidv.translator.nextReadingOrder
import dev.davidv.translator.sizeBytesForUri
import dev.davidv.translator.ui.components.DetectedRegions
import dev.davidv.translator.ui.components.ImageWordSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TranslatorViewModel(
  private val appContext: Context,
  private val translationCoordinator: TranslationCoordinator,
  val settingsManager: SettingsManager,
  val filePathManager: FilePathManager,
  catalogRepository: CatalogRepository,
  val languageMetadataManager: LanguageMetadataManager,
  initialText: String,
  initialLaunchMode: LaunchMode,
) : ViewModel() {
  val languageStateManager = LanguageStateManager(viewModelScope, catalogRepository)

  // UI state
  private val _input = MutableStateFlow(initialText)
  val input: StateFlow<String> = _input.asStateFlow()

  private val _output = MutableStateFlow<TranslatedText?>(null)
  val output: StateFlow<TranslatedText?> = _output.asStateFlow()

  private val _selection = MutableStateFlow(selectionRules().initial())
  val selection: StateFlow<MainSelection> = _selection.asStateFlow()

  private val _displayImage = MutableStateFlow<Bitmap?>(null)
  val displayImage: StateFlow<Bitmap?> = _displayImage.asStateFlow()

  private val _originalImage = MutableStateFlow<Bitmap?>(null)
  val originalImage: StateFlow<Bitmap?> = _originalImage.asStateFlow()

  private val _imageWordSelection = MutableStateFlow<ImageWordSelection?>(null)
  val imageWordSelection: StateFlow<ImageWordSelection?> = _imageWordSelection.asStateFlow()

  private val _detectedRegions = MutableStateFlow<DetectedRegions?>(null)
  val detectedRegions: StateFlow<DetectedRegions?> = _detectedRegions.asStateFlow()

  private data class OcrCacheEntry(
    val plan: PreparedImageOverlay,
    val imageRef: Bitmap,
    val sourceCode: String,
    val sourceScript: Script,
    val readingOrder: ReadingOrder?,
    // Owns the source pixels rust-side so a language switch re-renders without re-OCR or any
    // image copy across the FFI. Null for images translated via the legacy copying path.
    val ocrImage: uniffi.bindings.OcrImage?,
  )

  private var ocrCache: OcrCacheEntry? = null

  private var translationJob: Job? = null

  // A cancelled image run's cleanup can land after its replacement has started, so each run
  // clears only its own token instead of a shared boolean.
  private class ImageRun

  private val activeImageRun = MutableStateFlow<ImageRun?>(null)
  val isImageProcessing: StateFlow<Boolean> =
    activeImageRun.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val _ocrReadingOrder = MutableStateFlow<ReadingOrder?>(null)
  val ocrReadingOrder: StateFlow<ReadingOrder?> = _ocrReadingOrder.asStateFlow()

  private val _inputType = MutableStateFlow(InputType.TEXT)
  val inputType: StateFlow<InputType> = _inputType.asStateFlow()

  // japaneseSpaced is read by TranslationService itself; it is carried here only so
  // toggling the setting invalidates the request and re-runs the transliteration.
  private data class TransliterationRequest(
    val text: String,
    val from: Language,
    val japaneseSpaced: Boolean,
  )

  @OptIn(ExperimentalCoroutinesApi::class)
  val inputTransliterated: StateFlow<String?> =
    combine(_input, _selection, _inputType, settingsManager.settings) { text, selection, inputType, settings ->
      val enabled = settings.showTransliterationOnInput && inputType == InputType.TEXT
      if (!enabled || selection !is MainSelection.Ready || text.isBlank()) {
        null
      } else {
        TransliterationRequest(text, selection.source, settings.addSpacesForJapaneseTransliteration)
      }
    }.distinctUntilChanged()
      .mapLatest { request ->
        if (request == null) {
          null
        } else {
          withContext(Dispatchers.Default) { translationCoordinator.transliterate(request.text, request.from) }
        }
      }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  // A text detection only stays valid while the input is at least as long as
  // the text that produced it; deleting below that point drops the detection
  // so a stale suggestion can't outlive the words it was based on.
  private data class LanguageDetection(
    val language: Language,
    val inputLength: Int,
  )

  private val _currentDetectedLanguage = MutableStateFlow<LanguageDetection?>(null)
  val currentDetectedLanguage: StateFlow<Language?> =
    _currentDetectedLanguage
      .map { it?.language }
      .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _isAutoSource = MutableStateFlow(false)
  val isAutoSource: StateFlow<Boolean> = _isAutoSource.asStateFlow()

  private val _currentLaunchMode = MutableStateFlow(initialLaunchMode)
  val currentLaunchMode: StateFlow<LaunchMode> = _currentLaunchMode.asStateFlow()

  private val _modalVisible = MutableStateFlow(initialLaunchMode == LaunchMode.Normal)
  val modalVisible: StateFlow<Boolean> = _modalVisible.asStateFlow()

  private val _dictionaryWord = MutableStateFlow<WordWithTaggedEntries?>(null)
  val dictionaryWord: StateFlow<WordWithTaggedEntries?> = _dictionaryWord.asStateFlow()

  private val _dictionaryStack = MutableStateFlow<List<WordWithTaggedEntries>>(emptyList())
  val dictionaryStack: StateFlow<List<WordWithTaggedEntries>> = _dictionaryStack.asStateFlow()

  private val _dictionaryLookupLanguage = MutableStateFlow<Language?>(null)
  val dictionaryLookupLanguage: StateFlow<Language?> = _dictionaryLookupLanguage.asStateFlow()

  @OptIn(ExperimentalCoroutinesApi::class)
  val ttsVoices: StateFlow<Map<String, List<uniffi.translator_core.InstalledTtsPack>>> =
    combine(_selection, languageStateManager.catalog) { selection, _ -> selection }
      .mapLatest { selection ->
        when (selection) {
          MainSelection.NoLanguages -> emptyMap()
          is MainSelection.Ready ->
            listOf(selection.source, selection.target)
              .distinct()
              .associate { it.code to translationCoordinator.installedTtsVoices(it) }
        }
      }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  private val _speech = MutableStateFlow<SpeechState>(SpeechState.Idle)
  val speech: StateFlow<SpeechState> = _speech.asStateFlow()

  private var speechJob: Job? = null

  // Playback reports are posted to the main thread in order, so the previous utterance's stop
  // always lands before the next one starts. Only Loading→Playing and Playing→Idle are real
  // transitions; anything else is a late report from an utterance that was already replaced.
  private val audioPlayer =
    PcmAudioPlayer { playing ->
      _speech.update { state ->
        when {
          playing && state is SpeechState.Loading -> SpeechState.Playing(state.target)
          !playing && state is SpeechState.Playing -> SpeechState.Idle
          else -> state
        }
      }
    }

  private val _documentTranslation = MutableStateFlow<DocumentTranslationUiState?>(null)
  val documentTranslation: StateFlow<DocumentTranslationUiState?> = _documentTranslation.asStateFlow()
  private var dismissedInProgressDocumentTaskId: Long? = null

  // Picked-but-not-yet-started document. Drives the Configure sheet
  // (source/dest + per-type options) before the translation service is
  // launched; cleared once the user confirms or cancels.
  private val _pendingDocument = MutableStateFlow<PendingDocument?>(null)
  val pendingDocument: StateFlow<PendingDocument?> = _pendingDocument.asStateFlow()

  // One-shot UI events (Toast, errors, etc.)
  private val _uiEvents = MutableSharedFlow<UiEvent>()
  val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

  private val _pendingSharedImage = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 1)
  val pendingSharedImage: SharedFlow<Uri> = _pendingSharedImage.asSharedFlow()

  // Deep link from the system TTS settings ("install voice data"). Conflated so a
  // request raised before the UI is listening is still delivered, exactly once.
  private val _languageManagerRequests = Channel<Unit>(Channel.CONFLATED)
  val languageManagerRequests: Flow<Unit> = _languageManagerRequests.receiveAsFlow()

  init {
    if (initialLaunchMode != LaunchMode.Normal) {
      _modalVisible.value = true
    }

    viewModelScope.launch {
      languageStateManager.fileEvents.collect { event ->
        handleFileEvent(event)
      }
    }

    viewModelScope.launch {
      DocumentTranslationService.documentTranslationState.collect { state ->
        if (state == null) {
          dismissedInProgressDocumentTaskId = null
          _documentTranslation.value = null
          return@collect
        }
        if (state.isTranslating && dismissedInProgressDocumentTaskId == state.taskId) {
          _documentTranslation.value = null
          return@collect
        }
        if (!state.isTranslating && dismissedInProgressDocumentTaskId == state.taskId) {
          dismissedInProgressDocumentTaskId = null
        }
        _documentTranslation.value = state.toUiState()
      }
    }

    viewModelScope.launch {
      languageStateManager.languageState.collect { availability ->
        _selection.update { selectionRules(availability).reconciled(it) }
      }
    }

    // Auto-translate initial text
    // Owned by translationJob so text typed before it finishes cancels it instead of being
    // overwritten by the initial text's late result.
    if (initialText.isNotBlank()) {
      translationJob =
        viewModelScope.launch {
          autoTranslateInitialText(initialText)
        }
    }

    viewModelScope.launch {
      _selection
        .filterIsInstance<MainSelection.Ready>()
        .map { it.source to it.target }
        .distinctUntilChanged()
        .collect { (from, to) -> translationCoordinator.preloadModel(from, to) }
    }
  }

  fun handleMessage(message: TranslatorMessage) {
    if (message !is TranslatorMessage.TextInput) {
      Log.d("HandleMessage", "Handle: $message")
    }

    when (message) {
      is TranslatorMessage.TextInput -> {
        if (_inputType.value == InputType.TEXT && _input.value == message.text) {
          return
        }
        if (_inputType.value != InputType.TEXT) {
          _displayImage.value = null
          _originalImage.value = null
          ocrCache = null
          _inputType.value = InputType.TEXT
        }
        _input.value = message.text
        if (message.text.isBlank()) {
          _currentDetectedLanguage.value = null
        }
        triggerTranslation()
      }

      is TranslatorMessage.FromLang -> {
        _isAutoSource.value = false
        updateSelection { current ->
          val carriedTarget = previousSourceAsTarget(current.source, message.language, message.change)
          selectionRules().normalized(message.language, carriedTarget?.let(current.targets::select) ?: current.targets)
        }
        _output.value = null
        triggerTranslation()
      }

      TranslatorMessage.EnableAutoSource -> {
        _isAutoSource.value = true
        _output.value = null
        triggerTranslation()
      }

      is TranslatorMessage.ToLang -> {
        updateSelection { selectionRules().normalized(it.source, it.targets.select(message.language)) }
        _output.value = null
        triggerTranslation()
      }

      is TranslatorMessage.AddTab -> {
        updateSelection { selectionRules().normalized(it.source, it.targets.add(message.language)) }
        _output.value = null
        triggerTranslation()
      }

      is TranslatorMessage.RemoveTab -> {
        val current = _selection.value as? MainSelection.Ready ?: return
        if (message.language !in current.targets.tabs || current.targets.tabs.size <= 1) return
        _selection.value = current.copy(targets = current.targets.remove(message.language))
        if (current.target == message.language) {
          _output.value = null
          triggerTranslation()
        }
      }

      is TranslatorMessage.SetImageUri -> {
        translationJob?.cancel()
        translationJob =
          viewModelScope.launch {
            val bm = translationCoordinator.correctBitmap(message.uri, message.deleteAfterLoad)
            ocrCache = null
            _originalImage.value = bm
            _displayImage.value = bm
            _inputType.value = InputType.IMAGE
            _currentDetectedLanguage.value = null
            _output.value = null
            val selection = _selection.value
            if (selection is MainSelection.Ready) {
              runImageTranslation(bm, selection.source, selection.target)
            }
          }
      }

      is TranslatorMessage.SetDocumentPath -> {
        handleDocumentPath(
          path = message.path,
          displayName = message.displayName,
          sizeBytes = message.sizeBytes,
          deleteAfterLoad = message.deleteAfterLoad,
        )
      }

      TranslatorMessage.SwapLanguages -> {
        val current = _selection.value
        if (current !is MainSelection.Ready || !languageStateManager.canSwapLanguages(current.source, current.target)) return
        _isAutoSource.value = false
        _selection.value = selectionRules().normalized(current.target, current.targets.select(current.source))
        _output.value = null
        triggerTranslation()
      }

      TranslatorMessage.ClearInput -> {
        _displayImage.value = null
        _output.value = null
        _input.value = ""
        _inputType.value = InputType.TEXT
        _originalImage.value = null
        ocrCache = null
        _currentDetectedLanguage.value = null
      }

      TranslatorMessage.ToggleJapaneseOcrMode -> {
        _ocrReadingOrder.value = nextReadingOrder(_ocrReadingOrder.value)
        val selection = _selection.value
        if (_inputType.value == InputType.IMAGE && selection is MainSelection.Ready && selection.source.code == "ja") {
          triggerTranslation()
        }
      }

      is TranslatorMessage.ImageTextDetected -> {
        _input.value = message.extractedText
      }

      is TranslatorMessage.DictionaryLookup -> {
        handleDictionaryLookup(message.str, message.language)
      }

      is TranslatorMessage.Steer -> {
        val selection = _selection.value as? MainSelection.Ready ?: return
        val src = _output.value?.source
        if (src.isNullOrBlank()) return
        viewModelScope.launch {
          when (val r = translationCoordinator.steer(selection.source, selection.target, src, message.forcedPrefix)) {
            is TranslationResult.Success -> _output.value = r.result
            is TranslationResult.Error -> Log.e("Steer", r.message)
          }
        }
      }

      is TranslatorMessage.PopDictionary -> {
        if (_dictionaryStack.value.size > 1) {
          _dictionaryStack.value = _dictionaryStack.value.dropLast(1)
          _dictionaryWord.value = _dictionaryStack.value.lastOrNull()
        } else {
          _dictionaryStack.value = emptyList()
          _dictionaryWord.value = null
          _dictionaryLookupLanguage.value = null
        }
        Log.d("PopDictionary", "Popped dictionary, stack size: ${_dictionaryStack.value.size}")
      }

      TranslatorMessage.ClearDictionaryStack -> {
        _dictionaryStack.value = emptyList()
        _dictionaryWord.value = null
        _dictionaryLookupLanguage.value = null
        Log.d("ClearDictionaryStack", "Cleared dictionary stack")
      }

      is TranslatorMessage.ChangeLaunchMode -> {
        _currentLaunchMode.value = message.newLaunchMode
        _modalVisible.value = message.newLaunchMode == LaunchMode.Normal
        Log.d("ChangeLaunchMode", "Changed launch mode to: ${message.newLaunchMode}")
      }

      TranslatorMessage.ShareTranslatedImage -> {
        val di = _displayImage.value
        if (di != null) {
          viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShareImage(di))
          }
        }
      }
    }
  }

  fun setSharedImageUri(uri: Uri) {
    _pendingSharedImage.tryEmit(uri)
  }

  fun requestLanguageManager() {
    _languageManagerRequests.trySend(Unit)
  }

  // A shared document (pdf/odt/txt/epub) reaches the same drawer as a picked one:
  // copy the content URI into cache off the main thread, then drive the pending
  // document state the file picker also feeds.
  fun setSharedDocumentUri(uri: Uri) {
    viewModelScope.launch {
      try {
        val file = withContext(Dispatchers.IO) { copyDocumentUriToCache(appContext, uri) }
        handleDocumentPath(
          path = file.absolutePath,
          displayName = displayNameForUri(appContext, uri) ?: file.name,
          sizeBytes = sizeBytesForUri(appContext, uri) ?: file.length(),
          deleteAfterLoad = true,
        )
      } catch (e: Exception) {
        Log.e("SharedDocument", "Failed to import shared document: $uri", e)
      }
    }
  }

  fun setModalVisible(visible: Boolean) {
    _modalVisible.value = visible
  }

  fun speak(
    text: String,
    language: Language,
    target: SpeechTarget,
  ) {
    speechJob?.cancel()
    audioPlayer.stop()
    _speech.value = SpeechState.Loading(target)
    speechJob =
      viewModelScope.launch {
        when (val result = translationCoordinator.synthesizeSpeech(language, text)) {
          is SpeechSynthesisResult.Success ->
            audioPlayer.play(result.audioChunks) { message ->
              _speech.value = SpeechState.Idle
              viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowToast(appContext.getString(R.string.translator_audio_playback_failed, message)))
              }
            }
          is SpeechSynthesisResult.Error -> {
            _speech.value = SpeechState.Idle
            _uiEvents.emit(UiEvent.ShowToast(speechErrorMessage(appContext, result.reason)))
          }
        }
      }
  }

  fun stopSpeech() {
    speechJob?.cancel()
    audioPlayer.stop()
    _speech.value = SpeechState.Idle
  }

  fun dismissDocumentTranslation() {
    val current = _documentTranslation.value
    if (current?.isTranslating == true) {
      dismissedInProgressDocumentTaskId = current.taskId
    }
    _documentTranslation.value = null
  }

  fun cancelDocumentTranslation() {
    DocumentTranslationService.cancel(appContext)
    dismissedInProgressDocumentTaskId = null
    _documentTranslation.value = null
    _inputType.value = InputType.TEXT
  }

  private fun triggerTranslation() {
    translationJob?.cancel()
    translationJob =
      viewModelScope.launch {
        val settings = settingsManager.settings.value
        if (!settings.disableCLD) {
          val text = _input.value
          if (text.isBlank()) {
            _currentDetectedLanguage.value = null
          } else {
            val prior = _currentDetectedLanguage.value?.takeIf { text.length >= it.inputLength }
            val detected =
              translationCoordinator.detectLanguageRobust(
                text,
                (_selection.value as? MainSelection.Ready)?.source,
                languageStateManager.languageState.value.allLanguages(),
              )
            _currentDetectedLanguage.value = detected?.let { LanguageDetection(it, text.length) } ?: prior
          }
          if (_isAutoSource.value) {
            val detected = _currentDetectedLanguage.value?.language
            if (detected != null && languageStateManager.languageState.value.availabilityFor(detected)?.translatorFiles == true) {
              updateSelection { current ->
                if (detected == current.target) current else selectionRules().normalized(detected, current.targets)
              }
            }
          }
        }
        // A language deletion can leave nothing to translate while detection was running.
        val selection = _selection.value as? MainSelection.Ready ?: return@launch
        translateWithLanguages(selection.source, selection.target)
      }
  }

  /**
   * Run a steer without committing it, for previewing what an alternative would
   * produce. Returns the fully re-decoded translation (not a spliced tail).
   */
  suspend fun steerPreview(forcedPrefix: String): String? {
    val selection = _selection.value as? MainSelection.Ready ?: return null
    val src = _output.value?.source
    if (src.isNullOrBlank()) return null
    return when (val r = translationCoordinator.steer(selection.source, selection.target, src, forcedPrefix)) {
      is TranslationResult.Success -> r.result.translated
      is TranslationResult.Error -> null
    }
  }

  private suspend fun runImageTranslation(
    bitmap: Bitmap,
    fromLang: Language,
    toLang: Language,
  ) {
    val run = ImageRun()
    activeImageRun.value = run
    try {
      translateImage(bitmap, fromLang, toLang)
    } finally {
      activeImageRun.compareAndSet(run, null)
    }
  }

  private suspend fun translateImage(
    bitmap: Bitmap,
    fromLang: Language,
    toLang: Language,
  ) {
    _imageWordSelection.value = null
    _detectedRegions.value = null
    val readingOrder = currentReadingOrderFor(fromLang)
    val cached =
      ocrCache?.takeIf { entry ->
        !_isAutoSource.value &&
          entry.imageRef === bitmap &&
          entry.readingOrder == readingOrder &&
          entry.sourceScript == fromLang.script
      }
    val onMessage: (TranslatorMessage.ImageTextDetected) -> Unit = { msg ->
      _input.value = msg.extractedText
    }
    val result =
      if (cached != null) {
        Log.d("OCR", "reusing OCR result for source=${fromLang.code} (script=${fromLang.script})")
        translationCoordinator.retranslateImageWithOverlay(
          cached.plan,
          fromLang,
          toLang,
          onMessage = onMessage,
          ocrImage = cached.ocrImage,
        )
      } else {
        val source =
          if (_isAutoSource.value && !settingsManager.settings.value.disableCLD) {
            OverlaySource.Auto(readingOrder)
          } else {
            OverlaySource.Fixed(fromLang, readingOrder)
          }
        translationCoordinator.translateImageWithOverlay(
          source,
          toLang,
          bitmap,
          onMessage = onMessage,
          onMissingDetectedLanguage = { detected ->
            _currentDetectedLanguage.value = LanguageDetection(detected, 0)
          },
          onDetectedRegions = { boxes, w, h ->
            _detectedRegions.value = DetectedRegions(w, h, boxes)
          },
        )
      }
    _detectedRegions.value = null
    result?.let {
      _displayImage.value = it.correctedBitmap
      _output.value = TranslatedText(it.translatedText, null)
      _imageWordSelection.value =
        ImageWordSelection(
          imageWidth = it.metadata.width.toInt(),
          imageHeight = it.metadata.height.toInt(),
          sourceWords = it.metadata.sourceWords,
          translatedWords = it.translatedWords,
        )
      ocrCache =
        OcrCacheEntry(
          plan = it.metadata,
          imageRef = bitmap,
          sourceCode = fromLang.code,
          sourceScript = fromLang.script,
          readingOrder = readingOrder,
          ocrImage = it.ocrImage,
        )
    }
  }

  private suspend fun translateWithLanguages(
    fromLang: Language,
    toLang: Language,
  ) {
    when (_inputType.value) {
      InputType.TEXT -> {
        val result = translationCoordinator.translateText(fromLang, toLang, _input.value.trim())
        when (result) {
          is TranslationResult.Success -> _output.value = result.result
          is TranslationResult.Error -> {
            _output.value = null
            _uiEvents.emit(UiEvent.ShowToast(appContext.getString(R.string.translation_error, result.message)))
          }
        }
      }

      InputType.IMAGE -> {
        originalImage.value?.let { bm ->
          runImageTranslation(bm, fromLang, toLang)
        }
      }

      InputType.FILE -> {
        _output.value = null
      }
    }
  }

  private fun translatedDocumentOutputFile(
    inputName: String,
    from: Language,
    to: Language,
  ): File {
    val inputFile = File(inputName)
    val extension = inputFile.extension.ifBlank { "txt" }
    val baseName = inputFile.nameWithoutExtension.ifBlank { "document" }
    val safeBaseName = baseName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "document" }
    return File(filePathManager.getTranslatedDocumentsDir(), "$safeBaseName.${from.code}-${to.code}.$extension")
  }

  private fun handleDocumentPath(
    path: String,
    displayName: String,
    sizeBytes: Long,
    deleteAfterLoad: Boolean,
  ) {
    Log.d("SetDocumentPath", "Selected document for translation: $displayName ($path)")
    _pendingDocument.value =
      PendingDocument(
        path = path,
        displayName = displayName,
        sizeBytes = sizeBytes,
        deleteAfterLoad = deleteAfterLoad,
        extension = File(path).extension.lowercase(),
      )
  }

  fun dismissPendingDocument() {
    val pending = _pendingDocument.value ?: return
    if (pending.deleteAfterLoad) {
      runCatching { File(pending.path).delete() }
    }
    _pendingDocument.value = null
  }

  fun startPendingDocumentTranslation(
    from: Language,
    to: Language,
    txtLayout: TxtLayoutChoice,
    translatePdfImages: Boolean,
  ) {
    val pending = _pendingDocument.value ?: return
    _pendingDocument.value = null

    _displayImage.value = null
    _originalImage.value = null
    ocrCache = null
    _output.value = null
    _input.value = ""
    _inputType.value = InputType.FILE
    _currentDetectedLanguage.value = null

    val outputFile = translatedDocumentOutputFile(pending.displayName, from, to)
    DocumentTranslationService.startTranslation(
      context = appContext,
      inputPath = pending.path,
      outputPath = outputFile.absolutePath,
      displayName = pending.displayName,
      sizeBytes = pending.sizeBytes,
      from = from,
      to = to,
      deleteAfterLoad = pending.deleteAfterLoad,
      translatePdfImages = translatePdfImages,
      txtLayout = txtLayout,
    )
  }

  private fun currentReadingOrderFor(fromLang: Language): ReadingOrder? =
    if (fromLang.code == "ja") {
      _ocrReadingOrder.value
    } else {
      null
    }

  private suspend fun autoTranslateInitialText(initialText: String) {
    val start = _selection.filterIsInstance<MainSelection.Ready>().first()
    val availability = languageStateManager.languageState.value
    val detected =
      if (!settingsManager.settings.value.disableCLD) {
        translationCoordinator.detectLanguageRobust(initialText, start.source, availability.allLanguages())
      } else {
        null
      }
    _currentDetectedLanguage.value = detected?.let { LanguageDetection(it, initialText.length) }
    if (detected != null) {
      if (availability.availabilityFor(detected)?.translatorFiles != true) return
      updateSelection { selectionRules().normalized(detected, it.targets) }
    }
    val selection = _selection.value as? MainSelection.Ready ?: return
    _output.value =
      when (val translated = translationCoordinator.translateText(selection.source, selection.target, initialText)) {
        is TranslationResult.Success -> translated.result
        is TranslationResult.Error -> null
      }
  }

  private fun handleDictionaryLookup(
    str: String,
    language: Language,
  ) {
    Log.i("DictionaryLookup", "Looking up $str for $language")
    val catalog = languageStateManager.catalog.value
    val foundWord =
      try {
        catalog.lookupDictionary(language, str)
      } catch (e: uniffi.bindings.CatalogException.MissingAsset) {
        viewModelScope.launch {
          _uiEvents.emit(UiEvent.ShowToast("No ${language.localizedName()} dictionary installed"))
        }
        return
      } catch (e: uniffi.bindings.CatalogException.Other) {
        Log.w("DictionaryLookup", "Lookup failed for ${language.displayName}", e)
        null
      }
    if (foundWord != null) {
      _dictionaryWord.value = foundWord
      _dictionaryLookupLanguage.value = language
      _dictionaryStack.value = _dictionaryStack.value + foundWord
      Log.d("DictionaryLookup", "From lookup got $foundWord")
    } else {
      viewModelScope.launch {
        _uiEvents.emit(UiEvent.ShowToast("'$str' not found in ${language.code} dictionary"))
      }
      Log.w("DictionaryLookup", "Lookup failed for ${language.displayName}")
    }
  }

  private fun handleFileEvent(event: FileEvent) {
    when (event) {
      is FileEvent.Error -> {
        viewModelScope.launch {
          _uiEvents.emit(UiEvent.ShowToast(event.message))
        }
        Log.w("TranslatorViewModel", "Error event: ${event.message}")
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioPlayer.release()
    // Recycle bitmaps
    _displayImage.value?.let { if (!it.isRecycled) it.recycle() }
    originalImage.value?.let { if (!it.isRecycled) it.recycle() }
  }

  private fun selectionRules(availability: LanguageAvailabilityState = languageStateManager.languageState.value) =
    MainSelectionRules(
      availability,
      settingsManager.settings.value,
      languageMetadataManager.metadata.value,
      languageStateManager::canTranslate,
    )

  // The settings screen can pick a target before any language is installed, and a running
  // translation can outlive the last language; with no pair yet there is nothing to change.
  private fun updateSelection(transform: (MainSelection.Ready) -> MainSelection.Ready) {
    _selection.update { current ->
      when (current) {
        MainSelection.NoLanguages -> current
        is MainSelection.Ready -> transform(current)
      }
    }
  }

  private fun previousSourceAsTarget(
    oldFrom: Language,
    newFrom: Language,
    change: FromLangChange,
  ): Language? {
    if (change != FromLangChange.MovePreviousToTarget) return null
    if (oldFrom == newFrom) return null
    if (!languageStateManager.canTranslate(newFrom, oldFrom)) return null
    return oldFrom
  }
}

sealed class UiEvent {
  data class ShowToast(val message: String) : UiEvent()

  data class ShareImage(val bitmap: Bitmap) : UiEvent()
}

enum class SpeechTarget { INPUT, OUTPUT }

sealed interface SpeechState {
  val target: SpeechTarget?

  data object Idle : SpeechState {
    override val target: SpeechTarget? get() = null
  }

  data class Loading(
    override val target: SpeechTarget,
  ) : SpeechState

  data class Playing(
    override val target: SpeechTarget,
  ) : SpeechState
}

data class PendingDocument(
  val path: String,
  val displayName: String,
  val sizeBytes: Long,
  val deleteAfterLoad: Boolean,
  val extension: String,
)

data class DocumentTranslationUiState(
  val taskId: Long,
  val fileName: String,
  val fileSizeBytes: Long,
  val outputPath: String? = null,
  val outputFileName: String? = null,
  val outputMimeType: String? = null,
  val errorMessage: String? = null,
  @StringRes val progressLabelRes: Int = R.string.doc_progress_preparing,
  val pdfPhases: PdfPhaseProgress? = null,
  val progressFraction: Float? = null,
) {
  val isTranslating: Boolean
    get() = outputPath == null && errorMessage == null
}

private fun DocumentTranslationServiceState.toUiState(): DocumentTranslationUiState =
  DocumentTranslationUiState(
    taskId = taskId,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    outputPath = outputPath,
    outputFileName = outputPath?.let { File(it).name },
    outputMimeType = outputPath?.let { mimeTypeForDocumentPath(it) },
    errorMessage = errorMessage,
    progressLabelRes = progressLabelRes,
    pdfPhases = pdfPhases,
    progressFraction = progressFraction,
  )

private fun mimeTypeForDocumentPath(path: String): String =
  when (File(path).extension.lowercase()) {
    "pdf" -> "application/pdf"
    "odt" -> "application/vnd.oasis.opendocument.text"
    "epub" -> "application/epub+zip"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
  }

class TranslatorViewModelFactory(
  private val appContext: Context,
  private val translationCoordinator: TranslationCoordinator,
  private val settingsManager: SettingsManager,
  private val filePathManager: FilePathManager,
  private val catalogRepository: CatalogRepository,
  private val languageMetadataManager: LanguageMetadataManager,
  private val initialText: String,
  private val initialLaunchMode: LaunchMode,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    TranslatorViewModel(
      appContext = appContext,
      translationCoordinator = translationCoordinator,
      settingsManager = settingsManager,
      filePathManager = filePathManager,
      catalogRepository = catalogRepository,
      languageMetadataManager = languageMetadataManager,
      initialText = initialText,
      initialLaunchMode = initialLaunchMode,
    ) as T
}

private fun speechErrorMessage(
  context: android.content.Context,
  reason: SpeechError,
): String =
  when (reason) {
    SpeechError.NothingToSpeak -> context.getString(R.string.tts_nothing_to_speak)
    is SpeechError.NoVoiceInstalled ->
      context.getString(R.string.tts_no_voice, reason.language.localizedName())
    is SpeechError.SynthesisFailed ->
      context.getString(R.string.tts_synthesis_failed, reason.language.localizedName())
    is SpeechError.SynthesisError ->
      context.getString(R.string.tts_synthesis_error, reason.language.localizedName(), reason.reason)
  }
