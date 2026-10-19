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
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.davidv.translator.CatalogRepository
import dev.davidv.translator.FileEvent
import dev.davidv.translator.FilePathManager
import dev.davidv.translator.LanguageMetadataManager
import dev.davidv.translator.LanguageStateManager
import dev.davidv.translator.LaunchMode
import dev.davidv.translator.SettingsManager
import dev.davidv.translator.TranslationCoordinator
import dev.davidv.translator.TranslatorMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TranslatorViewModel(
  appContext: Context,
  translationCoordinator: TranslationCoordinator,
  val settingsManager: SettingsManager,
  filePathManager: FilePathManager,
  catalogRepository: CatalogRepository,
  val languageMetadataManager: LanguageMetadataManager,
  initialText: String,
  initialLaunchMode: LaunchMode,
) : ViewModel() {
  val languageStateManager = LanguageStateManager(viewModelScope, catalogRepository)

  private val eventSink = UiEvents(viewModelScope)
  val uiEvents: SharedFlow<UiEvent> = eventSink.flow

  val translation =
    TranslationSession(
      scope = viewModelScope,
      appContext = appContext,
      translationCoordinator = translationCoordinator,
      settingsManager = settingsManager,
      languageStateManager = languageStateManager,
      languageMetadataManager = languageMetadataManager,
      uiEvents = eventSink,
      initialText = initialText,
    )

  val dictionary = DictionarySession(languageStateManager.catalog, eventSink)

  val documents =
    DocumentSession(
      scope = viewModelScope,
      appContext = appContext,
      filePathManager = filePathManager,
      onStarted = translation::showDocument,
      onCancelled = translation::leaveDocument,
    )

  val speech =
    SpeechController(
      scope = viewModelScope,
      appContext = appContext,
      translationCoordinator = translationCoordinator,
      uiEvents = eventSink,
      selection = translation.selection,
      catalog = languageStateManager.catalog,
    )

  private val _currentLaunchMode = MutableStateFlow(initialLaunchMode)
  val currentLaunchMode: StateFlow<LaunchMode> = _currentLaunchMode.asStateFlow()

  private val _modalVisible = MutableStateFlow(true)
  val modalVisible: StateFlow<Boolean> = _modalVisible.asStateFlow()

  private val _pendingSharedImage = MutableSharedFlow<Uri>(replay = 1, extraBufferCapacity = 1)
  val pendingSharedImage: SharedFlow<Uri> = _pendingSharedImage.asSharedFlow()

  // Deep link from the system TTS settings ("install voice data"). Conflated so a
  // request raised before the UI is listening is still delivered, exactly once.
  private val _languageManagerRequests = Channel<Unit>(Channel.CONFLATED)
  val languageManagerRequests: Flow<Unit> = _languageManagerRequests.receiveAsFlow()

  private val _liveCameraRequests = Channel<Unit>(Channel.CONFLATED)
  val liveCameraRequests: Flow<Unit> = _liveCameraRequests.receiveAsFlow()

  init {
    viewModelScope.launch {
      languageStateManager.fileEvents.collect { event ->
        when (event) {
          is FileEvent.Error -> {
            eventSink.send(UiEvent.ShowToast(event.message))
            Log.w("TranslatorViewModel", "Error event: ${event.message}")
          }
        }
      }
    }
  }

  fun handleMessage(message: TranslatorMessage) {
    if (message !is TranslatorMessage.TextInput) {
      Log.d("HandleMessage", "Handle: $message")
    }

    when (message) {
      is TranslatorMessage.PairMessage -> translation.handle(message)
      TranslatorMessage.ClearInput -> translation.clearInput()
      TranslatorMessage.ShareTranslatedImage -> translation.shareImage()
      is TranslatorMessage.SetDocumentPath ->
        documents.offer(message.path, message.displayName, message.sizeBytes, message.deleteAfterLoad)
      is TranslatorMessage.DictionaryLookup -> dictionary.lookup(message.str, message.language)
      TranslatorMessage.PopDictionary -> dictionary.pop()
      TranslatorMessage.ClearDictionaryStack -> dictionary.clear()
      is TranslatorMessage.ChangeLaunchMode -> {
        _currentLaunchMode.value = message.newLaunchMode
        _modalVisible.value = message.newLaunchMode == LaunchMode.Normal
        Log.d("ChangeLaunchMode", "Changed launch mode to: ${message.newLaunchMode}")
      }
    }
  }

  fun setSharedImageUri(uri: Uri) {
    _pendingSharedImage.tryEmit(uri)
  }

  fun requestLanguageManager() {
    _languageManagerRequests.trySend(Unit)
  }

  fun requestLiveCamera() {
    _liveCameraRequests.trySend(Unit)
  }

  fun setModalVisible(visible: Boolean) {
    _modalVisible.value = visible
  }

  override fun onCleared() {
    super.onCleared()
    speech.release()
    translation.release()
  }
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
