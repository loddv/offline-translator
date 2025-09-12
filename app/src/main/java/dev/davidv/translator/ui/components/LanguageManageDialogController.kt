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

package dev.davidv.translator.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.davidv.translator.DownloadState
import dev.davidv.translator.LangAvailability
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageAvailabilityState

data class LanguageManageDialogController(
  val handleEvent: (LanguageEvent) -> Unit,
)

@Composable
fun rememberLanguageManageDialog(
  languageState: LanguageAvailabilityState,
  downloadStates: Map<Language, DownloadState>,
  onEvent: (LanguageEvent) -> Unit,
): LanguageManageDialogController {
  var dialogLanguage by remember { mutableStateOf<Language?>(null) }

  val controller =
    remember(onEvent) {
      LanguageManageDialogController(
        handleEvent = { event ->
          when (event) {
            is LanguageEvent.Download -> {
              dialogLanguage = event.language
            }
            is LanguageEvent.Manage -> {
              dialogLanguage = event.language
            }
            else -> onEvent(event)
          }
        },
      )
    }

  dialogLanguage?.let { language ->
    LanguageManageDialog(
      language = language,
      availability =
        languageState.availableLanguageMap[language]
          ?: LangAvailability(false, false, false),
      downloadStates = downloadStates,
      onEvent = { event ->
        onEvent(event)
      },
      onDismiss = { dialogLanguage = null },
    )
  }

  return controller
}
