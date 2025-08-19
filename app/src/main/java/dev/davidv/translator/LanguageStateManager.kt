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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

      val availabilityMap =
        withContext(Dispatchers.IO) {
          buildMap {
            // English is always available
            put(Language.ENGLISH.code, true)

            Language.entries.forEach { fromLang ->
              val toLang = Language.ENGLISH
              if (fromLang != toLang) {
                val isAvailable = checkLanguagePairFiles(context, fromLang, toLang)
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

      _languageState.value =
        LanguageAvailabilityState(
          hasLanguages = hasLanguages,
          availableLanguages = availableLanguages,
          availableLanguageMap = availabilityMap,
          isChecking = false,
        )
    }
  }

  fun getFirstAvailableFromLanguage(excluding: Language? = null): Language? {
    val state = _languageState.value
    return state.availableLanguages
      .filterNot { it == excluding || it == Language.ENGLISH }
      .firstOrNull()
  }
}
