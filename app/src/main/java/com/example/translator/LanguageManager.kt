package com.example.translator

import android.content.Context
import android.content.res.Configuration
import android.util.Log
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream


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
fun LanguageManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded

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
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = if (status.isDownloading) Arrangement.Center else Arrangement.Center
                            ) {
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
                                modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center
                            ) {
                                if (status.isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp
                                    )
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (!isFullyDownloaded) {
                                                scope.launch {
                                                    languageStates[status.language] =
                                                        status.copy(isDownloading = true)

                                                    // Download translation pairs and tessdata
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
                                                    if (!status.tessDownloaded) {
                                                        // Ensure english is always downloaded for tess recognition
                                                        downloadTessData(context, Language.ENGLISH)
                                                        downloadTessData(context, status.language)
                                                    }

                                                    languageStates[status.language] =
                                                        LanguageStatus(
                                                            language = status.language,
                                                            toEnglishDownloaded = true,
                                                            fromEnglishDownloaded = true,
                                                            tessDownloaded = true,
                                                            isDownloading = false
                                                        )
                                                }
                                            }
                                        }, enabled = !isFullyDownloaded
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
    val hasAll = File(dataPath, model).exists() && File(dataPath, vocab).exists() && File(
        dataPath, lex
    ).exists()
    println("checking $from $to = $hasAll")
    return hasAll
}

fun checkTessDataFile(context: Context, language: Language): Boolean {
    val tessDataPath = File(context.filesDir, "tesseract/tessdata")
    val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
    val exists = tessFile.exists()
    println("checking tessdata for ${language.displayName} (${language.tessName}) = $exists")
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

private suspend fun downloadLanguagePair(context: Context, from: Language, to: Language) {
    val (model, vocab, lex) = filesFor(from, to)
    val files = listOf(model, vocab, lex)
    val lang = "${from.code}${to.code}"
    val dataPath = File(context.filesDir, "bin")
    dataPath.mkdirs()
    val ref = "4886b27f3c9756fff56005e7abe3fbfa34461209"
    val base =
        "https://media.githubusercontent.com/media/mozilla/firefox-translations-models/${ref}/models"

    val modelQuality = if (from == Language.ENGLISH) {
        fromEnglish[to]
    } else {
        toEnglish[from]
    }

    if (modelQuality == null) {
        println("Could not find model quality for ${from} -> ${to}")
        return
    }
    // Wait for all downloads to complete
    withContext(Dispatchers.IO) {
        files.forEach { fileName ->
            val file = File(dataPath, fileName)
            if (!file.exists()) {
                val url = "${base}/${modelQuality.toString()}/${lang}/${fileName}.gz"
                val success = downloadAndDecompress(url, file)
                println("Downloading ${url} to ${file} = $success")
            } else {
                println("File $file existed, not downloading")
            }
        }
    }


}

private suspend fun downloadTessData(context: Context, language: Language) {
    val tessDataPath = File(context.filesDir, "tesseract/tessdata")
    if (!tessDataPath.isDirectory) {
        tessDataPath.mkdirs()
    }
    val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
    val url =
        "https://github.com/tesseract-ocr/tessdata_fast/raw/refs/heads/main/${language.tessName}.traineddata"
    if (!tessFile.exists()) {
        withContext(Dispatchers.IO) {
            val success = download(url, tessFile)
            Log.i(
                "LanguageManager",
                "Downloaded tessdata for ${language.displayName} = ${url}: $success"
            )
        }
    } else {
        Log.i("LanguageManager", "Tessdata for ${language.displayName} already exists")
    }
}

suspend fun download(url: String, outputFile: File) = withContext(Dispatchers.IO) {
    try {
        URL(url).openStream().use { input ->
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        true
    } catch (e: Exception) {
        Log.e("Download", "Error downloading file", e)
        false
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