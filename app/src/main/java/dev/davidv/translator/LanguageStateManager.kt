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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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

          is DownloadEvent.LanguageDeleted -> {
            deleteLanguage(event.language)
          }

          is DownloadEvent.DictionaryIndexLoaded -> {
            _dictionaryIndex.value = event.index
            Log.i("LanguageStateManager", "Dictionary index loaded: ${event.index != null}")
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
            // English is always available FIXME
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
    updatedLanguageMap[language] = LangAvailability(true, true, updatedLanguageMap[language]?.dictionaryFiles ?: false)

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

  private fun deleteLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    updatedLanguageMap[language] = LangAvailability(translatorFiles = false, ocrFiles = false, dictionaryFiles = false)

    val hasLanguages = updatedLanguageMap.any { it.key != Language.ENGLISH && it.value.translatorFiles }

    _languageState.value =
      currentState.copy(
        hasLanguages = hasLanguages,
        availableLanguageMap = updatedLanguageMap,
      )

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
          loadDictionaryIndexFromFile()
        }
      _dictionaryIndex.value = index
      Log.i("LanguageStateManager", "Dictionary index loaded from file: ${index != null}")
    }
  }

  private fun loadDictionaryIndexFromFile(): DictionaryIndex? {
    return try {
      val indexFile = filePathManager.getDictionaryIndexFile()
      if (!indexFile.exists()) return null

      val jsonString = indexFile.readText()
      val jsonObject = org.json.JSONObject(jsonString)

      val dictionariesJson = jsonObject.getJSONObject("dictionaries")
      val dictionaries = mutableMapOf<String, DictionaryInfo>()

      for (key in dictionariesJson.keys()) {
        val dictJson = dictionariesJson.getJSONObject(key)
        dictionaries[key] =
          DictionaryInfo(
            date = dictJson.getLong("date"),
            filename = dictJson.getString("filename"),
            size = dictJson.getLong("size"),
            type = dictJson.getString("type"),
            wordCount = dictJson.getLong("word_count"),
          )
      }

      DictionaryIndex(
        dictionaries = dictionaries,
        updatedAt = jsonObject.getLong("updated_at"),
        version = jsonObject.getInt("version"),
      )
    } catch (e: Exception) {
      Log.e("LanguageStateManager", "Error parsing dictionary index file", e)
      null
    }
  }
}
