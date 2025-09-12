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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.DownloadState
import dev.davidv.translator.LangAvailability
import dev.davidv.translator.Language
import dev.davidv.translator.R
import dev.davidv.translator.ui.theme.TranslatorTheme

@Composable
fun LanguageManageDialog(
  language: Language,
  availability: LangAvailability,
  downloadStates: Map<Language, DownloadState>,
  onEvent: (LanguageEvent) -> Unit,
  onDismiss: () -> Unit,
) {
  val dictionaryDownloadState = downloadStates[language]

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = language.displayName,
        style = MaterialTheme.typography.headlineSmall,
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Translations",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (availability.translatorFiles) "Installed" else "Not installed",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (availability.translatorFiles) {
            IconButton(
              onClick = { onEvent(LanguageEvent.Delete(language)) },
            ) {
              Icon(
                painterResource(id = R.drawable.delete),
                contentDescription = "Delete Translations",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          } else {
            IconButton(
              onClick = { onEvent(LanguageEvent.Download(language)) },
            ) {
              Icon(
                painterResource(id = R.drawable.add),
                contentDescription = "Download Translations",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Dictionary",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = if (availability.dictionaryFiles) "Installed" else "Not installed",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (availability.dictionaryFiles) {
            IconButton(
              onClick = { onEvent(LanguageEvent.DeleteDictionary(language)) },
            ) {
              Icon(
                painterResource(id = R.drawable.delete),
                contentDescription = "Delete Dictionary",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          } else {
            val isDictionaryDownloading = dictionaryDownloadState?.isDownloading == true
            if (isDictionaryDownloading) {
              IconButton(
                onClick = { onEvent(LanguageEvent.Cancel(language)) },
              ) {
                Icon(
                  painterResource(id = R.drawable.cancel),
                  contentDescription = "Cancel Dictionary Download",
                )
              }
            } else {
              IconButton(
                onClick = { onEvent(LanguageEvent.DownloadDictionary(language)) },
              ) {
                Icon(
                  painterResource(id = R.drawable.add),
                  contentDescription = "Download Dictionary",
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

@Preview(showBackground = true)
@Composable
fun LanguageManageDialogPreview() {
  TranslatorTheme {
    LanguageManageDialog(
      language = Language.SPANISH,
      availability =
        LangAvailability(
          translatorFiles = true,
          ocrFiles = true,
          dictionaryFiles = false,
        ),
      downloadStates = emptyMap(),
      onEvent = {},
      onDismiss = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
fun LanguageManageDialogFullyInstalledPreview() {
  TranslatorTheme {
    LanguageManageDialog(
      language = Language.FRENCH,
      availability =
        LangAvailability(
          translatorFiles = true,
          ocrFiles = true,
          dictionaryFiles = true,
        ),
      downloadStates = emptyMap(),
      onEvent = {},
      onDismiss = {},
    )
  }
}
