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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun StyledTextField(
  text: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  readOnly: Boolean = false,
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
  Box(modifier = modifier.fillMaxSize()) {
    BasicTextField(
      value = text,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxSize(),
      textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
      readOnly = readOnly,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      decorationBox = { innerTextField ->
        if (text.isEmpty() && placeholder != null) {
          Text(
            text = placeholder,
            style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
          )
        }
        innerTextField()
      },
    )
  }
}

@Preview(showBackground = true)
@Composable
fun StyledTextFieldWeightedPreview() {
  TranslatorTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      StyledTextField(
        text = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
        onValueChange = {},
        placeholder = "Enter text",
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}
