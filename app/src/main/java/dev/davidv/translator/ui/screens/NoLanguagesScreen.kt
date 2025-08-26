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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.DownloadService
import dev.davidv.translator.FilePathManager
import dev.davidv.translator.LanguageManagerScreen
import dev.davidv.translator.LanguageStateManager
import dev.davidv.translator.R
import dev.davidv.translator.SettingsManager
import dev.davidv.translator.ui.theme.TranslatorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoLanguagesScreen(
  onDone: () -> Unit,
  onSettings: () -> Unit,
  languageStateManager: LanguageStateManager,
  downloadService: DownloadService?,
) {
  val state by languageStateManager.languageState.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Language Setup") },
        actions = {
          IconButton(onClick = onSettings) {
            Icon(
              painterResource(id = R.drawable.settings),
              contentDescription = "Settings",
            )
          }
        },
      )
    },
    bottomBar = {
      Button(
        onClick = onDone,
        enabled = state.hasLanguages,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(8.dp),
      ) {
        Text("Done")
      }
    },
  ) { paddingValues ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 16.dp),
    ) {
      Text(
        text = "Download language packs to start translating",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
      )

      LanguageManagerScreen(
        embedded = true,
        languageState = languageStateManager.languageState,
        downloadStates_ = downloadService?.downloadStates,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun NoLanguagesScreenPreview() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settingsManager = SettingsManager(context)
  val filePathManager = FilePathManager(context, settingsManager.settings)
  TranslatorTheme {
    NoLanguagesScreen(
      onDone = {},
      onSettings = {},
      languageStateManager = LanguageStateManager(scope, filePathManager, DownloadService()),
      downloadService = DownloadService(),
    )
  }
}
