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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.components.LanguageDownloadButton
import java.io.File

@Composable
@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
fun LanguageManagerPreview() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val settingsManager = SettingsManager(context)
  val filePathManager = FilePathManager(context, settingsManager.settings)
  LanguageManagerScreen(languageStateManager = LanguageStateManager(scope, filePathManager))
}

@Composable
fun LanguageManagerScreen(
  embedded: Boolean = false,
  languageStateManager: LanguageStateManager,
) {
  val context = LocalContext.current
  var downloadService by remember { mutableStateOf<DownloadService?>(null) }

  val serviceConnection =
    remember {
      object : ServiceConnection {
        override fun onServiceConnected(
          name: ComponentName?,
          service: IBinder?,
        ) {
          val binder = service as DownloadService.DownloadBinder
          downloadService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
          downloadService = null
        }
      }
    }

  // Bind to service
  DisposableEffect(context) {
    val intent = Intent(context, DownloadService::class.java)
    context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

    onDispose {
      context.unbindService(serviceConnection)
    }
  }

  // Get language availability from centralized state manager
  val languageAvailabilityState by languageStateManager.languageState.collectAsState()

  // Get download states from service
  val downloadStates by downloadService?.downloadStates?.collectAsState()
    ?: remember { mutableStateOf(emptyMap()) }

  // Show toast for download errors
  LaunchedEffect(downloadStates) {
    downloadStates.values.forEach { downloadState ->
      if (downloadState.error != null) {
        Toast
          .makeText(
            context,
            "Download failed: ${downloadState.error}",
            Toast.LENGTH_LONG,
          ).show()
      }
    }
  }

  // Connect download service to language state manager
  LaunchedEffect(downloadService) {
    languageStateManager.setDownloadService(downloadService)
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
  ) { paddingValues ->

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .then(if (!embedded) Modifier.padding(paddingValues) else Modifier)
          .padding(horizontal = 16.dp, vertical = if (embedded) 0.dp else 16.dp),
    ) {
      if (!embedded) {
        Text(
          text = "Language Packs",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(bottom = 16.dp),
        )
      }

      // Separate languages into installed and available
      val installedLanguages = languageAvailabilityState.availableLanguages.filter { it != Language.ENGLISH }.sortedBy { it.displayName }
      val availableLanguages =
        Language.entries
          .filter { lang ->
            fromEnglishFiles[lang] != null && !languageAvailabilityState.availableLanguages.contains(lang) && lang != Language.ENGLISH
          }.sortedBy { it.displayName }

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        // Installed Languages Section
        if (installedLanguages.isNotEmpty()) {
          item {
            Text(
              text = "Installed",
              style = MaterialTheme.typography.titleLarge,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }

          items(installedLanguages) { lang ->
            LanguageItem(
              lang = lang,
              fullyDownloaded = true,
              downloadState = downloadStates[lang],
              context = context,
            )
          }
        }

        // Available Languages Section
        if (availableLanguages.isNotEmpty()) {
          if (!embedded || installedLanguages.isNotEmpty()) {
            item {
              Text(
                text = "Available",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
              )
            }
          }

          items(availableLanguages) { lang ->
            LanguageItem(
              fullyDownloaded = false,
              lang = lang,
              downloadState = downloadStates[lang],
              context = context,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LanguageItem(
  lang: Language,
  fullyDownloaded: Boolean,
  downloadState: DownloadState?,
  context: Context,
) {
  Row(
    modifier =
      Modifier
        .padding(0.dp)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = lang.displayName,
      style = MaterialTheme.typography.titleMedium,
    )
    LanguageDownloadButton(lang, downloadState, context, fullyDownloaded)
  }
}

fun missingFiles(
  dataPath: File,
  lang: Language,
): List<String> = missingFilesTo(dataPath, lang).plus(missingFilesFrom(dataPath, lang))

fun missingFilesFrom(
  dataPath: File,
  lang: Language,
): List<String> {
  val allPaths = fromEnglishFiles[lang]!!.allFiles().map { File(dataPath, it) }
  val presentPaths = allPaths.filter { !it.exists() }.map { it.name }
  return presentPaths
}

fun missingFilesTo(
  dataPath: File,
  lang: Language,
): List<String> {
  val allPaths = toEnglishFiles[lang]!!.allFiles().map { File(dataPath, it) }
  val presentPaths = allPaths.filter { !it.exists() }.map { it.name }
  return presentPaths
}

fun getAvailableTessLanguages(tessData: File): List<Language> = Language.entries.filter { File(tessData, it.tessFilename).exists() }
