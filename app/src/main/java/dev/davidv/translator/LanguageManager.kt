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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


data class LanguageStatus(
    val language: Language,
    var toEnglishDownloaded: Boolean = false,
    var fromEnglishDownloaded: Boolean = false,
    var tessDownloaded: Boolean = false,
    var isDownloading: Boolean = false
)

@Composable
@Preview(
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES
)
fun LanguageManagerPreview() {
    LanguageManagerScreen()
}

@Composable
fun LanguageManagerScreen(
    onLanguageDownloaded: () -> Unit = {},
    onLanguageDeleted: () -> Unit = {},
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    var downloadService by remember { mutableStateOf<DownloadService?>(null) }

    // Service connection
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
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
    val languageStates = remember {
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
                Toast.makeText(
                    context,
                    "Download failed: ${downloadState.error}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Refresh local status when downloads complete or when states change
    LaunchedEffect(downloadStates) {
        downloadStates.values.forEach { downloadState ->
            val language = downloadState.language

            // Refresh status when download completes or when state is reset (indicating deletion)
            if (downloadState.isCompleted ||
                (!downloadState.isDownloading && !downloadState.isCancelled && downloadState.error == null)
            ) {

                withContext(Dispatchers.IO) {
                    val toEnglishDownloaded =
                        checkLanguagePairFiles(context, language, Language.ENGLISH)
                    val fromEnglishDownloaded =
                        checkLanguagePairFiles(context, Language.ENGLISH, language)
                    val tessDownloaded = checkTessDataFile(context, language)

                    languageStates[language] = LanguageStatus(
                        language = language,
                        toEnglishDownloaded = toEnglishDownloaded,
                        fromEnglishDownloaded = fromEnglishDownloaded,
                        tessDownloaded = tessDownloaded
                    )

                    // Notify when first language is downloaded
                    if (downloadState.isCompleted && (toEnglishDownloaded || fromEnglishDownloaded)) {
                        onLanguageDownloaded()
                    }

                    // Notify when language is deleted (state reset and no files)
                    if (!downloadState.isDownloading && !downloadState.isCompleted &&
                        !toEnglishDownloaded && !fromEnglishDownloaded && !tessDownloaded
                    ) {

                        // Check if ALL languages are now deleted
                        val allLanguagesDeleted = languageStates.values.all { status ->
                            !status.toEnglishDownloaded && !status.fromEnglishDownloaded && !status.tessDownloaded
                        }

                        if (allLanguagesDeleted) {
                            onLanguageDeleted()
                        }
                    }
                }
            }
        }
    }

    // Check which language pairs are already downloaded
    LaunchedEffect(Unit) {
        languageStates.keys.forEach { language ->
            val toEnglishDownloaded = withContext(Dispatchers.IO) {
                checkLanguagePairFiles(context, language, Language.ENGLISH)
            }
            val fromEnglishDownloaded = withContext(Dispatchers.IO) {
                checkLanguagePairFiles(context, Language.ENGLISH, language)
            }
            val tessDownloaded = withContext(Dispatchers.IO) {
                checkTessDataFile(context, language)
            }
            languageStates[language] = LanguageStatus(
                language = language,
                toEnglishDownloaded = toEnglishDownloaded,
                fromEnglishDownloaded = fromEnglishDownloaded,
                tessDownloaded = tessDownloaded
            )
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!embedded) Modifier.padding(paddingValues) else Modifier)
                .padding(horizontal = 16.dp, vertical = if (embedded) 0.dp else 16.dp)
        ) {
            if (!embedded) {
                Text(
                    text = "Language Packs",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Separate languages into installed and available
            val allLanguages = languageStates.values.toList().sortedBy { it.language.displayName }
            val installedLanguages = allLanguages.filter { status ->
                status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
            }
            val availableLanguages = allLanguages.filterNot { status ->
                status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Installed Languages Section
                if (installedLanguages.isNotEmpty()) {
                    item {
                        Text(
                            text = "Installed",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(installedLanguages) { status ->
                        LanguageItem(
                            status = status,
                            downloadState = downloadStates[status.language],
                            downloadService = downloadService,
                            context = context
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
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    items(availableLanguages) { status ->
                        LanguageItem(
                            status = status,
                            downloadState = downloadStates[status.language],
                            downloadService = downloadService,
                            context = context
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
    downloadService: DownloadService?,
    context: Context
) {
    val isFullyDownloaded =
        status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
    val isDownloading = downloadState?.isDownloading == true
    val isCompleted = downloadState?.isCompleted == true

    Row(
        modifier = Modifier
            .padding(0.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = status.language.displayName,
            style = MaterialTheme.typography.titleMedium
        )
        Row {
            if (isDownloading) {
                // Cancel button with progress indicator around it
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    val targetProgress = downloadState?.progress ?: 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 300),
                        label = "progress"
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(48.dp)
                    )
                    IconButton(
                        onClick = {
                            downloadService?.cancelDownload(status.language)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painterResource(id = R.drawable.cancel),
                            contentDescription = "Cancel Download",
                        )
                    }
                }
            } else if (isFullyDownloaded || isCompleted) {
                // Delete button for completed downloads
                IconButton(
                    onClick = {
                        downloadService?.deleteLanguage(status.language)
                    },
                ) {
                    Icon(
                        painterResource(id = R.drawable.delete),
                        contentDescription = "Delete Language",
                    )
                }
            } else {
                // Download/retry button
                IconButton(
                    onClick = {
                        DownloadService.startDownload(
                            context, status.language
                        )

                    }, enabled = true
                ) {
                    when {
                        downloadState?.isCancelled == true || downloadState?.error != null -> {
                            Icon(
                                painterResource(id = R.drawable.refresh),
                                contentDescription = "Retry Download"
                            )
                        }

                        else -> {
                            Icon(
                                painterResource(id = R.drawable.add),
                                contentDescription = "Download"
                            )
                        }
                    }
                }
            }

        }
    }
}

fun checkLanguagePairFiles(context: Context, from: Language, to: Language): Boolean {
    val dataPath = File(context.filesDir, "bin")
    val (model, vocab, lex) = filesFor(from, to)
    val hasAll = File(dataPath, model).exists() && File(dataPath, vocab).exists() && File(
        dataPath, lex
    ).exists()
    return hasAll
}

fun checkTessDataFile(context: Context, language: Language): Boolean {
    val tessDataPath = File(context.filesDir, "tesseract/tessdata")
    val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
    val exists = tessFile.exists()
    return exists
}

fun getAvailableTessLanguages(context: Context): String {
    val availableLanguages = Language.entries.filter { language ->
        checkTessDataFile(context, language)
    }.map { it.tessName }

    val languageString = availableLanguages.joinToString("+")
    Log.i("LanguageManager", "Available tess languages: $languageString")
    return languageString
}


fun filesFor(from: Language, to: Language): Triple<String, String, String> {

    val lang = "${from.code}${to.code}"
    // vocab lang is *en for es, bg, fr, et, de (models/prod/enes/vocab.esen.spm.gz)
    val vocabLang = if (from == Language.ENGLISH && listOf(
            Language.SPANISH,
            Language.BULGARIAN,
            Language.FRENCH,
            Language.ESTONIAN,
            Language.GERMAN
        ).contains(to)
    ) {
        "${to.code}${from.code}"
    } else {
        "${from.code}${to.code}"
    }
    val model = "model.$lang.intgemm.alphas.bin"
    val vocab = "vocab.$vocabLang.spm" // sometimes it's flipped SAD
    val lex = "lex.50.50.$lang.s2t.bin"
    return Triple(model, vocab, lex)
}