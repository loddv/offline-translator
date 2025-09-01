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

package dev.davidv.translator.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.AppSettings
import dev.davidv.translator.DownloadState
import dev.davidv.translator.Language
import dev.davidv.translator.LaunchMode
import dev.davidv.translator.R
import dev.davidv.translator.TranslatedText
import dev.davidv.translator.TranslatorMessage
import dev.davidv.translator.ui.components.DetectedLanguageSection
import dev.davidv.translator.ui.components.ImageCaptureHandler
import dev.davidv.translator.ui.components.InputSection
import dev.davidv.translator.ui.components.LanguageSelectionRow
import dev.davidv.translator.ui.components.TranslationField
import dev.davidv.translator.ui.components.ZoomableImageViewer
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun MainScreen(
  // Navigation
  onSettings: () -> Unit,
  // Current state (read-only)
  input: String,
  output: TranslatedText?,
  from: Language,
  to: Language,
  detectedLanguage: Language?,
  displayImage: Bitmap?,
  isTranslating: StateFlow<Boolean>,
  isOcrInProgress: StateFlow<Boolean>,
  launchMode: LaunchMode,
  // Action requests
  onMessage: (TranslatorMessage) -> Unit,
  // System integration
  availableLanguages: Map<String, Boolean>,
  downloadStates: Map<Language, DownloadState> = emptyMap(),
  settings: AppSettings,
) {
  var showFullScreenImage by remember { mutableStateOf(false) }
  var showImageSourceSheet by remember { mutableStateOf(false) }
  val translating by isTranslating.collectAsState()
  val extraTopPadding = if (launchMode == LaunchMode.Normal) 0.dp else 8.dp

  Scaffold(
    floatingActionButton = {
      when (launchMode) {
        LaunchMode.Normal -> {
          if (!settings.disableOcr) {
            FloatingActionButton(onClick = {
              showImageSourceSheet = true
            }) {
              Icon(
                painterResource(id = R.drawable.add_photo),
                contentDescription = "Translate image",
              )
            }
          }
        }

        LaunchMode.ReadonlyModal -> {
        }

        is LaunchMode.ReadWriteModal -> {
          if (output != null) {
            FloatingActionButton(
              onClick = {
                launchMode.reply(output.translated)
              },
              shape = FloatingActionButtonDefaults.largeShape,
            ) {
              Icon(
                painterResource(id = R.drawable.check),
                contentDescription = "Replace text",
                modifier = Modifier.size(30.dp),
              )
            }
          }
        }
      }
    },
  ) { paddingValues ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .navigationBarsPadding()
          .imePadding()
          .padding(top = paddingValues.calculateTopPadding() + extraTopPadding, bottom = 8.dp),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
      ) {
        LanguageSelectionRow(
          from = from,
          to = to,
          availableLanguages = availableLanguages,
          translating = translating,
          onMessage = onMessage,
          onSettings = if (launchMode == LaunchMode.Normal) onSettings else null,
        )

        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(0.4f),
        ) {
          InputSection(
            displayImage = displayImage,
            input = input,
            isOcrInProgress = isOcrInProgress,
            isTranslating = isTranslating,
            onMessage = onMessage,
            onShowFullScreenImage = { showFullScreenImage = true },
          )
        }

        DetectedLanguageSection(
          detectedLanguage = detectedLanguage,
          from = from,
          availableLanguages = availableLanguages,
          onMessage = onMessage,
          downloadStates = downloadStates,
        )
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.5f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(0.5f),
        ) {
          if (output != null) {
            TranslationField(
              text = output,
            )
          }
        }
      }
    }
  }

  // Image capture handling
  ImageCaptureHandler(
    onMessage = onMessage,
    showImageSourceSheet = showImageSourceSheet,
    onDismissImageSourceSheet = { showImageSourceSheet = false },
  )

  // Full screen image viewer
  if (showFullScreenImage && displayImage != null) {
    ZoomableImageViewer(
      bitmap = displayImage,
      onDismiss = { showFullScreenImage = false },
    )
  }
}

@Composable
fun WideDialogTheme(content: @Composable () -> Unit) {
  TranslatorTheme {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Transparent),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier =
          Modifier
            .fillMaxWidth(0.9f)
            .height((LocalConfiguration.current.screenHeightDp * 0.5f).dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
      ) {
        content()
      }
    }
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PopupMode() {
  WideDialogTheme {
    MainScreen(
      onSettings = { },
      input = "Example input",
      output = TranslatedText("Example output", null),
      from = Language.AZERBAIJANI,
      to = Language.SPANISH,
      detectedLanguage = Language.FRENCH,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.ReadWriteModal {},
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MainScreenPreview() {
  TranslatorTheme {
    MainScreen(
      onSettings = { },
      input = "Example input",
      output = TranslatedText("Example output", null),
      from = Language.ENGLISH,
      to = Language.SPANISH,
      detectedLanguage = Language.FRENCH,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.Normal,
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PreviewVeryLongText() {
  TranslatorTheme {
    MainScreen(
      onSettings = { },
      input = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
      output =
        TranslatedText(
          "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
          null,
        ),
      from = Language.ENGLISH,
      to = Language.ENGLISH,
      detectedLanguage = null,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.Normal,
    )
  }
}
