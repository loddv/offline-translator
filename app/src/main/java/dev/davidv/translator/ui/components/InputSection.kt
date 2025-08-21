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
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.R
import dev.davidv.translator.TranslatorMessage
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun InputSection(
  displayImage: Bitmap?,
  input: String,
  isOcrInProgress: StateFlow<Boolean>,
  isTranslating: StateFlow<Boolean>,
  onMessage: (TranslatorMessage) -> Unit,
  onShowFullScreenImage: () -> Unit,
) {
  if (displayImage != null) {
    Box(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column {
        val isOcrInProgressState by isOcrInProgress.collectAsState()
        val isTranslatingState by isTranslating.collectAsState()
        val isProcessing = isOcrInProgressState || isTranslatingState

        if (isProcessing) {
          LinearProgressIndicator(
            modifier =
              Modifier
                .fillMaxWidth(),
          )
        }

        Image(
          bitmap = displayImage.asImageBitmap(),
          contentDescription = "Image to translate",
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { onShowFullScreenImage() },
        )
      }

      IconButton(
        onClick = { onMessage(TranslatorMessage.ClearImage) },
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(32.dp),
      ) {
        Icon(
          painterResource(id = R.drawable.cancel),
          contentDescription = "Remove image",
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
      }
    }
  } else {
    StyledTextField(
      text = input,
      onValueChange = { newInput ->
        onMessage(TranslatorMessage.TextInput(newInput))
      },
      placeholder = "Enter text",
      modifier =
        Modifier
          .fillMaxSize(),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun InputSectionTextPreview() {
  TranslatorTheme {
    InputSection(
      displayImage = null,
      input = "Hello, this is some sample text for translation",
      isOcrInProgress = MutableStateFlow(false),
      isTranslating = MutableStateFlow(false),
      onMessage = {},
      onShowFullScreenImage = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
fun InputSectionProcessingTextPreview() {
  TranslatorTheme {
    InputSection(
      displayImage = null,
      input = "Processing translation...",
      isOcrInProgress = MutableStateFlow(false),
      isTranslating = MutableStateFlow(true),
      onMessage = {},
      onShowFullScreenImage = {},
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun InputSectionDarkPreview() {
  TranslatorTheme {
    InputSection(
      displayImage = null,
      input = "Dark mode text input",
      isOcrInProgress = MutableStateFlow(false),
      isTranslating = MutableStateFlow(false),
      onMessage = {},
      onShowFullScreenImage = {},
    )
  }
}
