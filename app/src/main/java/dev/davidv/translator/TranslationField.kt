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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun TranslationField(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                if (text.isNotEmpty()) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                            .padding(end = 20.dp) // Leave space for copy button
                    ) {
                        Text(
                            text = text,
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Copy button positioned sticky to the right
                    IconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Translation", text)
                            clipboard.setPrimaryClip(clip)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                    ) {
                        Icon(
                            painterResource(id = R.drawable.copy),
                            contentDescription = "Copy translation",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
    }
}


@Preview(showBackground = true)
@Composable
fun TranslationFieldBothWeightedPreview() {
    TranslatorTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TranslationField(
                text = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}