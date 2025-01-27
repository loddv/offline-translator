package com.example.translator

import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
//ibs.androidx.material.icons.extended.android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.tooling.preview.Preview
import com.example.translator.MainActivity.Language
import java.io.File


data class LanguageStatus(
    val language: Language,
    var toEnglishDownloaded: Boolean = false,
    var fromEnglishDownloaded: Boolean = false,
    var isDownloading: Boolean = false
)

@Composable
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
fun LanguageManagerPreview() {
    LanguageManagerScreen()
}

@Composable
fun LanguageManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Track status for each language
    val languageStates = remember {
        mutableStateMapOf<Language, LanguageStatus>().apply {
            Language.values().forEach { lang ->
                if (lang != Language.ENGLISH) {
                    put(lang, LanguageStatus(lang))
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
            languageStates[language] = LanguageStatus(
                language = language,
                toEnglishDownloaded = toEnglishDownloaded,
                fromEnglishDownloaded = fromEnglishDownloaded
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Language Packs",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                languageStates.values.toList()
                    .sortedBy { item -> item.language.displayName }) { status ->
                val isFullyDownloaded = status.toEnglishDownloaded && status.fromEnglishDownloaded

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = status.language.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (!isFullyDownloaded && !status.isDownloading) {
                                Text(
                                    text = "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (status.isDownloading) {
                                Text(
                                    text = "Downloading...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (status.isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            FilledTonalIconButton(
                                onClick = {
                                    if (!isFullyDownloaded) {
                                        scope.launch {
                                            languageStates[status.language] =
                                                status.copy(isDownloading = true)

                                            // Download both directions if needed
                                            if (!status.toEnglishDownloaded) {
                                                downloadLanguagePair(
                                                    context,
                                                    status.language,
                                                    Language.ENGLISH
                                                )
                                            }
                                            if (!status.fromEnglishDownloaded) {
                                                downloadLanguagePair(
                                                    context,
                                                    Language.ENGLISH,
                                                    status.language
                                                )
                                            }

                                            languageStates[status.language] = LanguageStatus(
                                                language = status.language,
                                                toEnglishDownloaded = true,
                                                fromEnglishDownloaded = true,
                                                isDownloading = false
                                            )
                                        }
                                    }
                                },
                                enabled = !isFullyDownloaded
                            ) {
                                if (isFullyDownloaded) {
                                    Icon(Icons.Default.Check, contentDescription = "Downloaded")
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = "Download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun checkLanguagePairFiles(context: Context, from: Language, to: Language): Boolean {
    val dataPath = context.getExternalFilesDir("bin")!!
    val (model, vocab, lex) = filesFor(from, to)

    return File(dataPath, model).exists() &&
            File(dataPath, vocab).exists() &&
            File(dataPath, lex).exists()
}

private suspend fun downloadLanguagePair(context: Context, from: Language, to: Language) {
    val (model, vocab, lex) = filesFor(from, to)
    val files = listOf(model, vocab, lex)
    val lang = "${from.code}${to.code}"
    val dataPath = context.getExternalFilesDir("bin")!!
    // fixme: 0.3.4 is not latest
    val base = "https://storage.googleapis.com/bergamot-models-sandbox/0.3.4"
//    val base = "https://media.githubusercontent.com/media/mozilla/firefox-translations-models/refs/heads/main/models/prod/"
    val downloadRequests = mutableListOf<Long>()
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    files.forEach { fileName ->
        val file = File(dataPath, fileName)
        if (!file.exists()) {
            val url = "${base}/${lang}/${fileName}"
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading $fileName")
                .setDestinationUri(Uri.fromFile(file))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION)


            println("Downloading ${url} to ${file}")
            downloadRequests.add(dm.enqueue(request))
        }
    }

    // Wait for all downloads to complete
    withContext(Dispatchers.IO) {
        downloadRequests.forEach { downloadId ->
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    downloading =
                        status != DownloadManager.STATUS_SUCCESSFUL && status != DownloadManager.STATUS_FAILED
                }
                cursor.close()
                if (downloading) {
                    kotlinx.coroutines.delay(100)
                }
            }
        }
    }
}


fun filesFor(from: Language, to: Language): Triple<String, String, String> {
    val lang = "${from.code}${to.code}"
    // vocab lang is always *en
    val vocabLang = if (from == Language.ENGLISH) {
        "${to.code}${from.code}"
    } else {
        "${from.code}${to.code}"
    }
    val model = "model.$lang.intgemm.alphas.bin"
    val vocab = "vocab.$lang.spm"
    val lex = "lex.50.50.$lang.s2t.bin"
    return Triple(model, vocab, lex)
}