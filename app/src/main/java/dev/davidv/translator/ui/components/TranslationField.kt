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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.R
import dev.davidv.translator.TranslatedText
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun TranslationField(
  text: TranslatedText,
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
  val context = LocalContext.current

  if (text.translated.isEmpty()) {
    return
  }
  Box(
    modifier =
      Modifier
        .fillMaxSize(),
  ) {
    SelectionContainer(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          // Leave space for copy button
          .padding(end = 22.dp),
    ) {
      Column {
        BasicTextField(
          value = text.translated,
          onValueChange = { },
          textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          modifier = Modifier.fillMaxSize(),
          readOnly = true,
        )

        if (text.transliterated != null) {
          BasicTextField(
            value = text.transliterated,
            onValueChange = { },
            textStyle =
              textStyle.copy(
                fontSize = textStyle.fontSize.times(0.7),
                color = MaterialTheme.colorScheme.onSurface,
              ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(top = 5.dp),
            readOnly = true,
          )
        }
      }
    }

    // Copy button positioned sticky to the right
    IconButton(
      onClick = {
        val clipboard =
          context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Translation", text.translated)
        clipboard.setPrimaryClip(clip)
      },
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .size(24.dp),
    ) {
      Icon(
        painterResource(id = R.drawable.copy),
        contentDescription = "Copy translation",
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
      )
    }
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun TranslationFieldBothWeightedPreview() {
  TranslatorTheme {
    Column(modifier = Modifier.fillMaxSize()) {
      TranslationField(
        text =
          TranslatedText(
            "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
            null,
          ),
      )
    }
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun WithTransliteration() {
  TranslatorTheme {
    Column(modifier = Modifier.fillMaxSize()) {
      TranslationField(
        text = TranslatedText("some words", "transliterated"),
      )
    }
  }
}
