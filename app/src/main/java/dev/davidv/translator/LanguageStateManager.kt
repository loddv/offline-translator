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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TODO: remove either the list or the map
data class LanguageAvailabilityState(
  val hasLanguages: Boolean = false,
  val availableLanguages: List<Language> = emptyList(),
  val availableLanguageMap: Map<String, Boolean> = emptyMap(),
  val isChecking: Boolean = true,
)

class LanguageStateManager(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  private val _languageState = MutableStateFlow(LanguageAvailabilityState())
  val languageState: StateFlow<LanguageAvailabilityState> = _languageState.asStateFlow()

  init {
    refreshLanguageAvailability()
  }

  fun refreshLanguageAvailability() {
    scope.launch {
      _languageState.value = _languageState.value.copy(isChecking = true)

      Log.i("LanguageStateManager", "Refreshing language availability")
      val availabilityMap =
        withContext(Dispatchers.IO) {
          buildMap {
            // English is always available
            put(Language.ENGLISH.code, true)

            Language.entries.forEach { fromLang ->
              val toLang = Language.ENGLISH
              if (fromLang != toLang) {
                val isAvailable = checkLanguagePairFiles(context, fromLang, toLang)
                Log.d("LanguageStateManager", "Language available ${fromLang.displayName}->${toLang.displayName}=$isAvailable")
                put(fromLang.code, isAvailable)
              }
            }
          }
        }

      val availableLanguages =
        Language.entries.filter { language ->
          availabilityMap[language.code] == true
        }

      val hasLanguages = availableLanguages.any { it != Language.ENGLISH }
      Log.i("LanguageStateManager", "hasLanguages = $hasLanguages")
      _languageState.value =
        LanguageAvailabilityState(
          hasLanguages = hasLanguages,
          availableLanguages = availableLanguages,
          availableLanguageMap = availabilityMap,
          isChecking = false,
        )
    }
  }

  fun addLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    updatedLanguageMap[language.code] = true

    val updatedAvailableLanguages =
      Language.entries.filter { lang ->
        updatedLanguageMap[lang.code] == true
      }

    val hasLanguages = updatedAvailableLanguages.any { it != Language.ENGLISH }

    _languageState.value =
      currentState.copy(
        hasLanguages = hasLanguages,
        availableLanguages = updatedAvailableLanguages,
        availableLanguageMap = updatedLanguageMap,
      )

    Log.i("LanguageStateManager", "Added language: ${language.displayName}")
  }

  fun deleteLanguage(language: Language) {
    val currentState = _languageState.value
    val updatedLanguageMap = currentState.availableLanguageMap.toMutableMap()
    updatedLanguageMap[language.code] = false

    val updatedAvailableLanguages =
      Language.entries.filter { lang ->
        updatedLanguageMap[lang.code] == true
      }

    val hasLanguages = updatedAvailableLanguages.any { it != Language.ENGLISH }

    _languageState.value =
      currentState.copy(
        hasLanguages = hasLanguages,
        availableLanguages = updatedAvailableLanguages,
        availableLanguageMap = updatedLanguageMap,
      )

    Log.i("LanguageStateManager", "Removed language: ${language.displayName}")
  }

  fun getFirstAvailableFromLanguage(excluding: Language? = null): Language? {
    val state = _languageState.value
    return state.availableLanguages
      .filterNot { it == excluding || it == Language.ENGLISH }
      .firstOrNull()
  }
}
