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

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.DownloadService
import dev.davidv.translator.DownloadState
import dev.davidv.translator.LangAvailability
import dev.davidv.translator.Language
import dev.davidv.translator.TranslatorMessage
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun DetectedLanguageSection(
  detectedLanguage: Language?,
  from: Language,
  availableLanguages: Map<Language, LangAvailability>,
  onMessage: (TranslatorMessage) -> Unit,
  downloadStates: Map<Language, DownloadState>,
) {
  val context = LocalContext.current

  if (detectedLanguage != null && detectedLanguage != from) {
    DetectedLanguageToast(
      detectedLanguage = detectedLanguage,
      availableLanguages = availableLanguages,
      onSwitchClick = {
        onMessage(TranslatorMessage.FromLang(detectedLanguage))
      },
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
      downloadStates = downloadStates,
      onEvent = { event ->
        when (event) {
          is LanguageEvent.Cancel -> DownloadService.cancelDownload(context, event.language)
          is LanguageEvent.Delete -> DownloadService.deleteLanguage(context, event.language)
          is LanguageEvent.Download -> DownloadService.startDownload(context, event.language)
          is LanguageEvent.Manage -> {}
        }
      },
    )
  }
}

@Preview(showBackground = true)
@Composable
fun DetectedLanguageSectionPreview() {
  TranslatorTheme {
    DetectedLanguageSection(
      detectedLanguage = Language.FRENCH,
      from = Language.ENGLISH,
      availableLanguages =
        mapOf(
          Language.ENGLISH to LangAvailability(true, true, true),
          Language.SPANISH to LangAvailability(true, true, true),
          Language.FRENCH to LangAvailability(true, true, true),
          Language.GERMAN to LangAvailability(false, true, true),
        ),
      onMessage = {},
      downloadStates = emptyMap(),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun DetectedLanguageSectionNoDetectionPreview() {
  TranslatorTheme {
    DetectedLanguageSection(
      detectedLanguage = null,
      from = Language.ENGLISH,
      availableLanguages =
        mapOf(
          Language.ENGLISH to LangAvailability(true, true, true),
          Language.SPANISH to LangAvailability(true, true, true),
          Language.FRENCH to LangAvailability(true, true, true),
        ),
      onMessage = {},
      downloadStates = emptyMap(),
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun DetectedLanguageSectionDarkPreview() {
  TranslatorTheme {
    DetectedLanguageSection(
      detectedLanguage = Language.GERMAN,
      from = Language.SPANISH,
      availableLanguages =
        mapOf(
          Language.ENGLISH to LangAvailability(true, true, true),
          Language.SPANISH to LangAvailability(true, true, true),
          Language.GERMAN to LangAvailability(true, true, true),
        ),
      onMessage = {},
      downloadStates = emptyMap(),
    )
  }
}
