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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.AppSettings
import dev.davidv.translator.CatalogRepository
import dev.davidv.translator.DownloadManager
import dev.davidv.translator.FilePathManager
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageMetadataManager
import dev.davidv.translator.LanguageStateManager
import dev.davidv.translator.R
import dev.davidv.translator.adblock.AdblockManager
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoLanguagesScreen(
  settingsManager: dev.davidv.translator.SettingsManager,
  onDone: () -> Unit,
  onSettings: () -> Unit,
  languageStateManager: LanguageStateManager,
  languageMetadataManager: dev.davidv.translator.LanguageMetadataManager,
  downloadManager: DownloadManager,
) {
  val state by languageStateManager.languageState.collectAsState()
  val context = LocalContext.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.nolang_title)) },
        actions = {
          IconButton(onClick = onSettings) {
            Icon(
              painterResource(id = R.drawable.settings),
              contentDescription = stringResource(R.string.a11y_settings),
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
            .padding(8.dp)
            .navigationBarsPadding(),
      ) {
        Text(stringResource(R.string.common_done))
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
        text = stringResource(R.string.nolang_prompt),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
      )

      LanguageAssetManagerScreen(
        context = context,
        languageStateManager = languageStateManager,
        languageMetadataManager = languageMetadataManager,
        settingsManager = settingsManager,
        downloadManager = downloadManager,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun NoLanguagesScreenPreview() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val appSettingsFlow = MutableStateFlow(AppSettings())
  val settingsManager = dev.davidv.translator.SettingsManager(context)
  val filePathManager = FilePathManager(context, appSettingsFlow)
  val catalogRepository = CatalogRepository.open(context, filePathManager)
  val downloadManager =
    DownloadManager(context, scope, catalogRepository, filePathManager, settingsManager, AdblockManager(filePathManager))
  val languagesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Language>>(emptyList())
  TranslatorTheme {
    NoLanguagesScreen(
      settingsManager = settingsManager,
      onDone = {},
      onSettings = {},
      downloadManager = downloadManager,
      languageStateManager = LanguageStateManager(scope, catalogRepository),
      languageMetadataManager = LanguageMetadataManager(context, languagesFlow),
    )
  }
}
