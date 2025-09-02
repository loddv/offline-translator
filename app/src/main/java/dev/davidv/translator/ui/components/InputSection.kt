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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
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
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  showOCRInput: Boolean = false,
) {
  Box(
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
      if (displayImage != null) {
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
      if (displayImage == null || showOCRInput) {
        StyledTextField(
          text = input,
          onValueChange = { newInput ->
            onMessage(TranslatorMessage.TextInput(newInput))
          },
          readOnly = displayImage != null,
          placeholder = if (displayImage == null) "Enter text" else null,
          modifier =
            Modifier
              .fillMaxSize()
              .padding(end = 24.dp),
        )
      }
    }

    // TODO: make this clearinput shared with StyledTextField
    if (displayImage != null || input.isNotEmpty()) {
      IconButton(
        onClick = { onMessage(TranslatorMessage.ClearInput) },
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .size(32.dp),
      ) {
        Icon(
          painterResource(id = R.drawable.cancel),
          contentDescription = "Clear input",
          tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
      }
    }
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
  val context = LocalContext.current
  val drawable = ContextCompat.getDrawable(context, R.drawable.example)
  val bitmap = drawable?.toBitmap()

  TranslatorTheme {
    InputSection(
      displayImage = bitmap,
      input = "this text was taken from the image",
      isOcrInProgress = MutableStateFlow(false),
      isTranslating = MutableStateFlow(true),
      onMessage = {},
      onShowFullScreenImage = {},
      showOCRInput = true,
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

@Preview(
  showBackground = true,
)
@Composable
fun InputWithTextAndImage() {
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
