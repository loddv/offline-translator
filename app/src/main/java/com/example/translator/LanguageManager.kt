package com.example.translator

import android.app.DownloadManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.translator.MainActivity.Language
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream


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
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    val isFullyDownloaded =
                        status.toEnglishDownloaded && status.fromEnglishDownloaded

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
                            Column(modifier = Modifier.weight(1f),
                                verticalArrangement = if (status.isDownloading) Arrangement.Center else Arrangement.Center) {
                                Text(
                                    text = status.language.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                 if (status.isDownloading) {
                                    Text(
                                        text = "Downloading...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
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
                                        Icon(
                                            painterResource(id = R.drawable.check),
                                            contentDescription = "Downloaded"
                                        )
                                    } else {
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
            }
        }
    }
}

fun checkLanguagePairFiles(context: Context, from: Language, to: Language): Boolean {
    val dataPath = File(context.filesDir, "bin")
    val (model, vocab, lex) = filesFor(from, to)
    val hasAll = File(dataPath, model).exists() &&
            File(dataPath, vocab).exists() &&
            File(dataPath, lex).exists()
    println("checking $from $to = $hasAll")
    return hasAll
}

private suspend fun downloadLanguagePair(context: Context, from: Language, to: Language) {
    val (model, vocab, lex) = filesFor(from, to)
    val files = listOf(model, vocab, lex)
    val lang = "${from.code}${to.code}"
    val dataPath = File(context.filesDir, "bin")
    dataPath.mkdirs()
    val base =
        "https://media.githubusercontent.com/media/mozilla/firefox-translations-models/main/models/prod"


    // Wait for all downloads to complete
    withContext(Dispatchers.IO) {
        files.forEach { fileName ->
            val file = File(dataPath, fileName)
            if (!file.exists()) {
                val url = "${base}/${lang}/${fileName}.gz"
                val success = downloadAndDecompress(url, file)
                println("Downloading ${url} to ${file} = $success")
            } else {
                println("File $file existed, not downloading")
            }
        }
    }


}

suspend fun downloadAndDecompress(url: String, outputFile: File) = withContext(Dispatchers.IO) {
    try {
        URL(url).openStream().use { input ->

            GZIPInputStream(input).use { gzipInput ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (gzipInput.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        }
        true
    } catch (e: Exception) {
        Log.e("Decompression", "Error decompressing file", e)
        false
    }
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