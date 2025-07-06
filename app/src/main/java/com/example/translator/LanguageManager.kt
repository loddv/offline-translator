package com.example.translator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
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

    // Refresh local status when downloads complete
    LaunchedEffect(downloadStates) {
        downloadStates.values.forEach { downloadState ->
            if (downloadState.isCompleted) {
                val language = downloadState.language
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
                    val downloadState = downloadStates[status.language]
                    val isFullyDownloaded =
                        status.toEnglishDownloaded && status.fromEnglishDownloaded && status.tessDownloaded
                    val isDownloading = downloadState?.isDownloading == true
                    val isCompleted = downloadState?.isCompleted == true

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
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = status.language.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (isDownloading) {
                                    LinearProgressIndicator(
                                        progress = { downloadState?.progress ?: 0f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                when {
                                    downloadState?.isCancelled == true -> {
                                        Text(
                                            text = "Download cancelled",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }

                                    downloadState?.error != null -> {
                                        Text(
                                            text = "Error: ${downloadState.error}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center
                            ) {
                                if (isDownloading) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            downloadService?.cancelDownload(status.language)
                                        },
                                    ) {
                                        Icon(
                                            painterResource(id = R.drawable.cancel),
                                            contentDescription = "Cancel Download",
                                        )
                                    }
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            if (!isFullyDownloaded) {
                                                DownloadService.startDownload(
                                                    context, status.language
                                                )
                                            }
                                        }, enabled = !isFullyDownloaded && !isCompleted
                                    ) {
                                        when {
                                            isFullyDownloaded || downloadState?.isCompleted == true -> {
                                                Icon(
                                                    painterResource(id = R.drawable.check),
                                                    contentDescription = "Downloaded"
                                                )
                                            }

                                            downloadState?.isCancelled == true -> {
                                                Icon(
                                                    painterResource(id = R.drawable.refresh),
                                                    contentDescription = "Retry Download"
                                                )
                                            }

                                            downloadState?.error != null -> {
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