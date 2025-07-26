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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream

class DownloadService : Service() {

    private val binder = DownloadBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val settingsManager by lazy { SettingsManager(this) }

    // Track download status for each language
    private val _downloadStates = MutableStateFlow<Map<Language, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Language, DownloadState>> = _downloadStates

    // Track download jobs for cancellation
    private val downloadJobs = mutableMapOf<Language, Job>()

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1001

        fun startDownload(context: Context, language: Language) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = "START_DOWNLOAD"
                putExtra("language_code", language.code)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, language: Language) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = "CANCEL_DOWNLOAD"
                putExtra("language_code", language.code)
            }
            context.startService(intent)
        }

        fun deleteLanguage(context: Context, language: Language) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = "DELETE_LANGUAGE"
                putExtra("language_code", language.code)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_DOWNLOAD" -> {
                val languageCode = intent.getStringExtra("language_code")
                val language = Language.entries.find { it.code == languageCode }
                if (language != null) {
                    startLanguageDownload(language)
                }
            }

            "CANCEL_DOWNLOAD" -> {
                val languageCode = intent.getStringExtra("language_code")
                val language = Language.entries.find { it.code == languageCode }
                if (language != null) {
                    cancelLanguageDownload(language)
                }
            }

            "DELETE_LANGUAGE" -> {
                val languageCode = intent.getStringExtra("language_code")
                val language = Language.entries.find { it.code == languageCode }
                if (language != null) {
                    deleteLanguageFiles(language)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startLanguageDownload(language: Language) {
        // Don't start if already downloading
        if (_downloadStates.value[language]?.isDownloading == true) return

        updateDownloadState(language) {
            it.copy(
                isDownloading = true,
                progress = 0f,
                error = null,
                isCancelled = false
            )
        }

        val job = serviceScope.launch {
            try {
                showNotification("Downloading ${language.displayName}", "Starting download...")

                val downloadTasks = mutableListOf<suspend () -> Unit>()

                // Prepare download tasks
                if (!checkLanguagePairFiles(this@DownloadService, language, Language.ENGLISH)) {
                    downloadTasks.add {
                        downloadLanguagePair(this@DownloadService, language, Language.ENGLISH)
                    }
                }

                if (!checkLanguagePairFiles(this@DownloadService, Language.ENGLISH, language)) {
                    downloadTasks.add {
                        downloadLanguagePair(this@DownloadService, Language.ENGLISH, language)
                    }
                }

                if (!checkTessDataFile(this@DownloadService, language)) {
                    downloadTasks.add {
                        downloadTessData(this@DownloadService, language)
                    }
                }

                // Always ensure English OCR is available
                if (!checkTessDataFile(this@DownloadService, Language.ENGLISH)) {
                    downloadTasks.add {
                        downloadTessData(this@DownloadService, Language.ENGLISH)
                    }
                }

                // Execute all downloads in parallel
                if (downloadTasks.isNotEmpty()) {
                    updateProgress(language, 0f, "Downloading ${downloadTasks.size} files in parallel...")
                    
                    val downloadJobs = downloadTasks.mapIndexed { index, task ->
                        async { 
                            task()
                            updateProgress(language, 1f / downloadTasks.size, "Downloaded index $index out of ${downloadTasks.size} files")
                        }
                    }
                    downloadJobs.awaitAll()
                }

                updateDownloadState(language) {
                    DownloadState(
                        language = language,
                        isDownloading = false,
                        isCompleted = true,
                        progress = 1f
                    )
                }

                showNotification("Download Complete", "${language.displayName} is ready to use")

            } catch (e: Exception) {
                Log.e("DownloadService", "Download failed for ${language.displayName}", e)
                updateDownloadState(language) {
                    it.copy(isDownloading = false, error = e.message)
                }
                showNotification("Download Failed", "${language.displayName} download failed")
            } finally {
                downloadJobs.remove(language)
            }
        }

        downloadJobs[language] = job
    }

    private fun cancelLanguageDownload(language: Language) {
        downloadJobs[language]?.cancel()
        downloadJobs.remove(language)

        updateDownloadState(language) {
            it.copy(isDownloading = false, isCancelled = true, error = null)
        }

        showNotification("Download Cancelled", "${language.displayName} download was cancelled")
        Log.i("DownloadService", "Cancelled download for ${language.displayName}")
    }

    private fun deleteLanguageFiles(language: Language) {
        serviceScope.launch {
            showNotification("Deleting ${language.displayName}", "Removing language files...")

            var deletedFiles = 0

            withContext(Dispatchers.IO) {
                // Delete translation files (to and from English)
                val dataPath = File(this@DownloadService.filesDir, "bin")

                // Delete to English files
                val (toModel, toVocab, toLex) = filesFor(language, Language.ENGLISH)
                listOf(toModel, toVocab, toLex).forEach { fileName ->
                    val file = File(dataPath, fileName)
                    if (file.exists() && file.delete()) {
                        deletedFiles++
                        Log.i("DownloadService", "Deleted: $fileName")
                    }
                }

                // Delete from English files
                val (fromModel, fromVocab, fromLex) = filesFor(Language.ENGLISH, language)
                listOf(fromModel, fromVocab, fromLex).forEach { fileName ->
                    val file = File(dataPath, fileName)
                    if (file.exists() && file.delete()) {
                        deletedFiles++
                        Log.i("DownloadService", "Deleted: $fileName")
                    }
                }

                // Delete tessdata file
                val tessDataPath = File(this@DownloadService.filesDir, "tesseract/tessdata")
                val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
                if (tessFile.exists() && tessFile.delete()) {
                    deletedFiles++
                    Log.i("DownloadService", "Deleted: ${tessFile.name}")
                }
            }

            // Clear the download state
            updateDownloadState(language) {
                DownloadState(
                    language = language,
                    isDownloading = false,
                    isCompleted = false,
                    isCancelled = false,
                    progress = 0f,
                    error = null
                )
            }

            showNotification(
                "Deletion Complete",
                "${language.displayName} files removed ($deletedFiles files)"
            )
            Log.i(
                "DownloadService",
                "Deleted ${language.displayName} - $deletedFiles files removed"
            )

        }
    }

    private fun updateProgress(language: Language, progress: Float, message: String) {
        updateDownloadState(language) { it.copy(progress = progress + it.progress) }
        showNotification("Downloading ${language.displayName}", message)
    }

    private fun updateDownloadState(language: Language, update: (DownloadState) -> DownloadState) {
        val currentStates = _downloadStates.value.toMutableMap()
        val currentState = currentStates[language] ?: DownloadState(language)
        currentStates[language] = update(currentState)
        _downloadStates.value = currentStates
    }

    private suspend fun downloadLanguagePair(context: Context, from: Language, to: Language) {
        val (model, vocab, lex) = filesFor(from, to)
        val files = listOf(model, vocab, lex)
        val lang = "${from.code}${to.code}"
        val dataPath = File(context.filesDir, "bin")
        dataPath.mkdirs()
        val base = settingsManager.settings.value.translationModelsBaseUrl

        val modelQuality = if (from == Language.ENGLISH) {
            fromEnglish[to]
        } else {
            toEnglish[from]
        }

        if (modelQuality == null) {
            Log.w("DownloadService", "Could not find model quality for ${from} -> ${to}")
            return
        }

        withContext(Dispatchers.IO) {
            val downloadJobs = files.map { fileName ->
                async {
                    val file = File(dataPath, fileName)
                    if (!file.exists()) {
                        val url = "${base}/${modelQuality}/${lang}/${fileName}.gz"
                        val success = downloadAndDecompress(url, file)
                        Log.i("DownloadService", "Downloaded ${url} to ${file} = $success")
                        success
                    } else {
                        true
                    }
                }
            }
            downloadJobs.awaitAll()
        }
    }

    private suspend fun downloadTessData(context: Context, language: Language) {
        val tessDataPath = File(context.filesDir, "tesseract/tessdata")
        if (!tessDataPath.isDirectory) {
            tessDataPath.mkdirs()
        }
        val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
        val url = "${settingsManager.settings.value.tesseractModelsBaseUrl}/${language.tessName}.traineddata"

        if (!tessFile.exists()) {
            withContext(Dispatchers.IO) {
                val success = download(url, tessFile)
                Log.i(
                    "DownloadService",
                    "Downloaded tessdata for ${language.displayName} = ${url}: $success"
                )
            }
        }
    }

    private suspend fun download(url: String, outputFile: File) = withContext(Dispatchers.IO) {
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
            Log.e("DownloadService", "Error downloading file", e)
            false
        }
    }

    private suspend fun downloadAndDecompress(url: String, outputFile: File) =
        withContext(Dispatchers.IO) {
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
                Log.e("DownloadService", "Error decompressing file", e)
                false
            }
        }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Language Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of language pack downloads"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.add) // You'll need to add an icon
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun cancelDownload(language: Language) {
        cancelLanguageDownload(language)
    }

    fun deleteLanguage(language: Language) {
        deleteLanguageFiles(language)
    }

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}

data class DownloadState(
    val language: Language,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null
)