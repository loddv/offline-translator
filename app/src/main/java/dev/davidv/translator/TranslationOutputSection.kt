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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun TranslationOutputSection(
    output: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.5f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }

    TranslationField(
        text = output,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun TranslationOutputSectionPreview() {
    TranslatorTheme {
        TranslationOutputSection(
            output = "This is the translated text output that appears in the translation field below the divider."
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TranslationOutputSectionEmptyPreview() {
    TranslatorTheme {
        TranslationOutputSection(
            output = ""
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun TranslationOutputSectionDarkPreview() {
    TranslatorTheme {
        TranslationOutputSection(
            output = "Translated text in dark mode with proper theming and contrast."
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TranslationOutputSectionLongTextPreview() {
    TranslatorTheme {
        TranslationOutputSection(
            output = "This is a very long translation that demonstrates how the component handles multiple lines of text. It should wrap properly and maintain good readability throughout the entire text content. The translation field should scroll if the content exceeds the available space."
        )
    }
}