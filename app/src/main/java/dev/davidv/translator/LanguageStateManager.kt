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

package dev.davidv.translator

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LangAvailability(
  val translatorFiles: Boolean,
  val ocrFiles: Boolean,
  val dictionaryFiles: Boolean,
)

data class LanguageAvailabilityState(
  val hasLanguages: Boolean = false,
  val availableLanguageMap: Map<Language, LangAvailability> = emptyMap(),
  val isChecking: Boolean = true,
)

fun isDictionaryAvailable(
  filePathManager: FilePathManager,
  language: Language,
): Boolean = filePathManager.getDictionaryFile(language).exists()

class LanguageStateManager(
  private val scope: CoroutineScope,
  private val filePathManager: FilePathManager,
  private val downloadEvents: SharedFlow<DownloadEvent>,
) {
  private val _languageState = MutableStateFlow(LanguageAvailabilityState())
  val languageState: StateFlow<LanguageAvailabilityState> = _languageState.asStateFlow()

  private val _dictionaryIndex = MutableStateFlow<DictionaryIndex?>(null)
  val dictionaryIndex: StateFlow<DictionaryIndex?> = _dictionaryIndex.asStateFlow()

  private val _fileEvents = MutableSharedFlow<FileEvent>()
  val fileEvents: SharedFlow<FileEvent> = _fileEvents.asSharedFlow()

  init {
    scope.launch {
      downloadEvents.collect { event ->
        when (event) {
          is DownloadEvent.NewTranslationAvailable -> {
            addTranslationLanguage(event.language)
          }

          is DownloadEvent.NewDictionaryAvailable -> {
            addDictionaryLanguage(event.language)
          }

          is DownloadEvent.DictionaryIndexDownloaded -> {
            _dictionaryIndex.value = event.index
            Log.i("LanguageStateManager", "Dictionary index downloaded: ${event.index}")
          }

          is DownloadEvent.DownloadError -> {
            Log.w("LanguageStateManager", "Download error: ${event.message}")
          }
        }
      }
    }
    refreshLanguageAvailability()
    loadDictionaryIndex()
  }

  fun refreshLanguageAvailability() {
    scope.launch {
      _languageState.value = _languageState.value.copy(isChecking = true)

      Log.i("LanguageStateManager", "Refreshing language availability")
      val availabilityMap =
        withContext(Dispatchers.IO) {
          buildMap {
            put(
              Language.ENGLISH,
              LangAvailability(
                translatorFiles = true,
                ocrFiles = true,
                dictionaryFiles = isDictionaryAvailable(filePathManager, Language.ENGLISH),
              ),
            )

            Language.entries.forEach { fromLang ->
              val toLang = Language.ENGLISH
              if (fromLang != toLang) {
                val dataPath = filePathManager.getDataDir()
                val isAvailable = missingFiles(dataPath, fromLang).second.isEmpty()
                put(
                  fromLang,
                  LangAvailability(
                    translatorFiles = isAvailable,
                    ocrFiles = isAvailable,
                    dictionaryFiles = isDictionaryAvailable(filePathManager, fromLang),
                  ),
                )
              }
            }
          }
        }

      val hasLanguages = availabilityMap.any { it.key != Language.ENGLISH && it.value.translatorFiles }
      Log.i("LanguageStateManager", "hasLanguages = $hasLanguages")
      _languageState.value =
        LanguageAvailabilityState(
          hasLanguages = hasLanguages,
          availableLanguageMap = availabilityMap,
          isChecking = false,
        )
    }
  }

  private fun addTranslationLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    val isDictAvail = isDictionaryAvailable(filePathManager, language)
    updatedLanguageMap[language] = LangAvailability(true, true, isDictAvail)

    // Only english doesn't count, so if it's non-english
    // or we already had languages, then we still have them
    val hasLanguages = _languageState.value.hasLanguages || language != Language.ENGLISH

    _languageState.value =
      currentState.copy(
        hasLanguages = hasLanguages,
        availableLanguageMap = updatedLanguageMap,
      )

    Log.i("LanguageStateManager", "Added translation language: ${language.displayName}")
  }

  private fun addDictionaryLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    val existingAvailability = updatedLanguageMap[language]
    updatedLanguageMap[language] =
      LangAvailability(
        translatorFiles = existingAvailability?.translatorFiles ?: false,
        ocrFiles = existingAvailability?.ocrFiles ?: false,
        dictionaryFiles = true,
      )

    _languageState.value =
      currentState.copy(
        availableLanguageMap = updatedLanguageMap,
      )

    Log.i("LanguageStateManager", "Added dict language: ${language.displayName}")
  }

  fun deleteDict(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    val existingAvailability = updatedLanguageMap[language]
    updatedLanguageMap[language] =
      LangAvailability(
        translatorFiles = existingAvailability?.translatorFiles ?: false,
        ocrFiles = existingAvailability?.ocrFiles ?: false,
        dictionaryFiles = false,
      )

    _languageState.value =
      currentState.copy(
        availableLanguageMap = updatedLanguageMap,
      )

    val dictionaryFile = filePathManager.getDictionaryFile(language)
    if (dictionaryFile.exists() && dictionaryFile.delete()) {
      Log.i("LanguageStateManager", "Deleted dictionary file: ${dictionaryFile.name}")
    }

    scope.launch {
      _fileEvents.emit(FileEvent.DictionaryDeleted(language))
    }

    Log.i("LanguageStateManager", "Removed dictionary for language: ${language.displayName}")
  }

  fun deleteLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    updatedLanguageMap[language] = LangAvailability(translatorFiles = false, ocrFiles = false, dictionaryFiles = false)

    val hasLanguages = updatedLanguageMap.any { it.key != Language.ENGLISH && it.value.translatorFiles }

    _languageState.value =
      currentState.copy(
        hasLanguages = hasLanguages,
        availableLanguageMap = updatedLanguageMap,
      )

    filePathManager.deleteLanguageFiles(language)
    scope.launch {
      _fileEvents.emit(FileEvent.LanguageDeleted(language))
    }
    Log.i("LanguageStateManager", "Removed language: ${language.displayName}")
  }

  fun getFirstAvailableFromLanguage(excluding: Language? = null): Language? {
    val state = _languageState.value
    return state.availableLanguages
      .filterNot { it.key == excluding }
      .filter { it.value.translatorFiles }
      .keys
      .firstOrNull()
  }

  private fun loadDictionaryIndex() {
    scope.launch {
      val index =
        withContext(Dispatchers.IO) {
          filePathManager.loadDictionaryIndexFromFile()
        }
      _dictionaryIndex.value = index
      if (index != null) {
        _fileEvents.emit(FileEvent.DictionaryIndexLoaded(index))
      }
      Log.i("LanguageStateManager", "Dictionary index loaded from file: ${index != null}")
    }
  }
}
