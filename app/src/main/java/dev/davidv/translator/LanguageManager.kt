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
import android.util.Log
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.components.LanguageDownloadButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LanguageStatus(
  val language: Language,
  var toEnglishDownloaded: Boolean = false,
  var fromEnglishDownloaded: Boolean = false,
  var tessDownloaded: Boolean = false,
  var isDownloading: Boolean = false,
)

data class FilesForLang(
  val model: String,
  val lex: String,
  val vocab: List<String>,
)

@Composable
@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
fun LanguageManagerPreview() {
  LanguageManagerScreen()
}

@Composable
fun LanguageManagerScreen(embedded: Boolean = false) {
  val context = LocalContext.current
  var downloadService by remember { mutableStateOf<DownloadService?>(null) }

  // Service connection
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

  // Track status for each language
  val languageStates =
    remember {
      mutableStateMapOf<Language, LanguageStatus>().apply {
        Language.entries.forEach { lang ->
          // fromEnglish and toEnglish are symmetrical by construction
          // (generate.py); so filter languages down to whichever has
          // translations available
          if (fromEnglish[lang] != null) {
            put(lang, LanguageStatus(lang))
          }
        }
      }
    }

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

  // Handle download events
  LaunchedEffect(downloadService) {
    downloadService?.downloadEvents?.collect { event ->
      when (event) {
        is DownloadEvent.NewLanguageAvailable -> {
          val language = event.language
          languageStates[language] =
            LanguageStatus(
              language = language,
              toEnglishDownloaded = true,
              fromEnglishDownloaded = true,
              tessDownloaded = true,
            )
        }

        is DownloadEvent.LanguageDeleted -> {
          val language = event.language
          languageStates[language] =
            LanguageStatus(
              language = language,
              toEnglishDownloaded = false,
              fromEnglishDownloaded = false,
              tessDownloaded = false,
            )
        }
      }
    }
  }

  // Check which language pairs are already downloaded
  LaunchedEffect(Unit) {
    languageStates.keys.forEach { language ->
      val toEnglishDownloaded =
        withContext(Dispatchers.IO) {
          checkLanguagePairFiles(context, language, Language.ENGLISH)
        }
      val fromEnglishDownloaded =
        withContext(Dispatchers.IO) {
          checkLanguagePairFiles(context, Language.ENGLISH, language)
        }
      val tessDownloaded =
        withContext(Dispatchers.IO) {
          checkTessDataFile(context, language)
        }
      languageStates[language] =
        LanguageStatus(
          language = language,
          toEnglishDownloaded = toEnglishDownloaded,
          fromEnglishDownloaded = fromEnglishDownloaded,
          tessDownloaded = tessDownloaded,
        )
    }
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
      val allLanguages =
        languageStates.values
          .toList()
          .filterNot { it.language == Language.ENGLISH }
          .sortedBy { it.language.displayName }
      val installedLanguages =
        allLanguages.filter { status ->
          status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
        }
      val availableLanguages =
        allLanguages.filterNot { status ->
          status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
        }

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

          items(installedLanguages) { status ->
            LanguageItem(
              status = status,
              downloadState = downloadStates[status.language],
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

          items(availableLanguages) { status ->
            LanguageItem(
              status = status,
              downloadState = downloadStates[status.language],
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
  status: LanguageStatus,
  downloadState: DownloadState?,
  context: Context,
) {
  val isFullyDownloaded =
    status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded

  Row(
    modifier =
      Modifier
        .padding(0.dp)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = status.language.displayName,
      style = MaterialTheme.typography.titleMedium,
    )
    LanguageDownloadButton(status.language, downloadState, context, isFullyDownloaded)
  }
}

fun checkLanguagePairFiles(
  context: Context,
  from: Language,
  to: Language,
): Boolean {
  val dataPath = File(context.filesDir, "bin")
  val files = filesFor(from, to)
  val hasAll =
    File(dataPath, files.model).exists() && File(dataPath, files.vocab[0]).exists() &&
      File(dataPath, files.vocab[1]).exists() &&
      File(dataPath, files.lex).exists()
  Log.d("LanguageManager", "For language ${from.displayName}-${to.displayName}, hasAll: $hasAll")

  return hasAll
}

fun checkTessDataFile(
  context: Context,
  language: Language,
): Boolean {
  val tessDataPath = File(context.filesDir, "tesseract/tessdata")
  val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
  val exists = tessFile.exists()
  Log.d("LanguageManager", "For language ${language.displayName}, tessdata: $exists")
  return exists
}

fun getAvailableTessLanguages(context: Context): String {
  val availableLanguages =
    Language.entries
      .filter { language ->
        checkTessDataFile(context, language)
      }.map { it.tessName }

  val languageString = availableLanguages.joinToString("+")
  Log.i("LanguageManager", "Available tess languages: $languageString")
  return languageString
}

fun filesFor(
  from: Language,
  to: Language,
): FilesForLang {
  val lang = "${from.code}${to.code}"
  val model = "model.$lang.intgemm.alphas.bin"
  val lex = "lex.50.50.$lang.s2t.bin"
  val vocabLang = "${from.code}${to.code}"

  val splitVocab = arrayListOf(Language.CHINESE, Language.JAPANESE)
  val vocab = arrayListOf<String>()
  if (splitVocab.contains(to)) {
    vocab.add("srcvocab.${from.code}${to.code}.spm")
    vocab.add("trgvocab.${from.code}${to.code}.spm")
  } else {
    vocab.add("vocab.$vocabLang.spm")
    vocab.add("vocab.$vocabLang.spm")
  }
  return FilesForLang(model, lex, vocab)
}
