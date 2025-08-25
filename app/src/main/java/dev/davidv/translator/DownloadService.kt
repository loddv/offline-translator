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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

class TrackingInputStream(
  private val inputStream: InputStream,
  private val size: Long,
  private val onProgress: (Float) -> Unit,
) : InputStream() {
  private var totalBytesRead = 0L
  private var lastReportedProgress = 0f

  override fun read(): Int {
    val byte = inputStream.read()
    if (byte != -1) {
      totalBytesRead++
      checkProgress()
    }
    return byte
  }

  override fun read(
    b: ByteArray,
    off: Int,
    len: Int,
  ): Int {
    val bytesRead = inputStream.read(b, off, len)
    if (bytesRead > 0) {
      totalBytesRead += bytesRead
      checkProgress()
    }
    return bytesRead
  }

  private fun checkProgress() {
    if (size > 0) {
      val currentProgress = totalBytesRead.toFloat() / size.toFloat()
      val incrementalProgress = currentProgress - lastReportedProgress
      if (incrementalProgress > 0.05f) {
        onProgress(incrementalProgress)
        lastReportedProgress = currentProgress
      }
    }
  }

  override fun close() {
    onProgress(1.0f - lastReportedProgress)
    inputStream.close()
  }
}

class DownloadService : Service() {
  private val binder = DownloadBinder()
  private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
  private val settingsManager by lazy { SettingsManager(this) }

  // Track download status for each language
  private val _downloadStates = MutableStateFlow<Map<Language, DownloadState>>(emptyMap())
  val downloadStates: StateFlow<Map<Language, DownloadState>> = _downloadStates

  // Event emission for download lifecycle changes
  private val _downloadEvents = MutableSharedFlow<DownloadEvent>()
  val downloadEvents: SharedFlow<DownloadEvent> = _downloadEvents.asSharedFlow()

  // Track download jobs for cancellation
  private val downloadJobs = mutableMapOf<Language, Job>()

  companion object {
    private const val CHANNEL_ID = "download_channel"
    private const val NOTIFICATION_ID = 1001

    fun startDownload(
      context: Context,
      language: Language,
    ) {
      val intent =
        Intent(context, DownloadService::class.java).apply {
          action = "START_DOWNLOAD"
          putExtra("language_code", language.code)
        }
      context.startService(intent)
    }

    fun cancelDownload(
      context: Context,
      language: Language,
    ) {
      val intent =
        Intent(context, DownloadService::class.java).apply {
          action = "CANCEL_DOWNLOAD"
          putExtra("language_code", language.code)
        }
      context.startService(intent)
    }

    fun deleteLanguage(
      context: Context,
      language: Language,
    ) {
      val intent =
        Intent(context, DownloadService::class.java).apply {
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

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int,
  ): Int {
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

    val job =
      serviceScope.launch {
        try {
//          showNotification("Downloading ${language.displayName}", "Starting download...")

          val downloadTasks = mutableListOf<suspend () -> Boolean>()
          val context = this@DownloadService
          val filePathManager = FilePathManager(context)
          val dataDir = filePathManager.getDataDir()
          val tessDir = filePathManager.getTesseractDataDir()
          Path(tessDir.absolutePath).createDirectories()
          val missingFrom = missingFilesFrom(dataDir, language)
          val missingTo = missingFilesTo(dataDir, language)
          val tessFile = File(tessDir, language.tessFilename)
          val engTessFile = File(tessDir, Language.ENGLISH.tessFilename)

          if (missingTo.isNotEmpty()) {
            downloadTasks.addAll(downloadLanguageFiles(dataDir, language, Language.ENGLISH, toEnglishFiles[language]!!.quality, missingTo))
          }

          if (missingFrom.isNotEmpty()) {
            downloadTasks.addAll(
              downloadLanguageFiles(
                dataDir,
                Language.ENGLISH,
                language,
                fromEnglishFiles[language]!!.quality,
                missingFrom,
              ),
            )
          }

          if (!tessFile.exists()) {
            downloadTasks.add { downloadTessData(this@DownloadService, language) }
          }

          // Always ensure English OCR is available
          if (!engTessFile.exists()) {
            downloadTasks.add { downloadTessData(this@DownloadService, Language.ENGLISH) }
          }

          // Execute all downloads in parallel
          var success = true
          if (downloadTasks.isNotEmpty()) {
            updateDownloadState(language) {
              it.copy(
                isDownloading = true,
                progress = 0.01f,
                taskCount = downloadTasks.count(),
              )
            }
            Log.i("DownloadService", "Starting ${downloadTasks.count()} download jobs")
            val downloadJobs = downloadTasks.map { task -> async { task() } }
            success = downloadJobs.awaitAll().all { it }
          }
          updateDownloadState(language) {
            DownloadState(
              language = language,
              isDownloading = false,
              isCompleted = success,
              progress = 1f,
            )
          }
          if (success) {
            showNotification("Download Complete", "${language.displayName} is ready to use")
            _downloadEvents.emit(DownloadEvent.NewLanguageAvailable(language))
          } else {
            showNotification("Download failed", "${language.displayName} download failed")
          }
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

      withContext(Dispatchers.IO) {
        // Delete translation files (to and from English)
        val dataPath = FilePathManager(this@DownloadService).getDataDir()

        // Delete to English files
        val toEnglishFiles = toEnglishFiles[language]
        toEnglishFiles?.allFiles()?.forEach { fileName ->
          val file = File(dataPath, fileName)
          if (file.exists() && file.delete()) {
            Log.i("DownloadService", "Deleted: $fileName")
          }
        }

        // Delete from English files
        val fromEnglishFiles = fromEnglishFiles[language]
        fromEnglishFiles?.allFiles()?.forEach { fileName ->
          val file = File(dataPath, fileName)
          if (file.exists() && file.delete()) {
            Log.i("DownloadService", "Deleted: $fileName")
          }
        }

        // Delete tessdata file
        val tessDataPath = FilePathManager(this@DownloadService).getTesseractDataDir()
        val tessFile = File(tessDataPath, language.tessFilename)
        if (tessFile.exists() && tessFile.delete()) {
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
          error = null,
        )
      }

      showNotification(
        "Deletion Complete",
        "${language.displayName} files removed",
      )
      _downloadEvents.emit(DownloadEvent.LanguageDeleted(language))
      Log.i(
        "DownloadService",
        "Deleted ${language.displayName}",
      )
    }
  }

  private fun updateDownloadState(
    language: Language,
    update: (DownloadState) -> DownloadState,
  ) {
    val currentStates = _downloadStates.value.toMutableMap()
    val currentState = currentStates[language] ?: DownloadState(language)
    currentStates[language] = update(currentState)
    _downloadStates.value = currentStates
  }

  private fun downloadLanguageFiles(
    dataPath: File,
    from: Language,
    to: Language,
    modelType: ModelType,
    files: List<String>,
  ): List<suspend () -> Boolean> {
    val base = settingsManager.settings.value.translationModelsBaseUrl
    val downloadJobs =
      files.mapNotNull { fileName ->
        val file = File(dataPath, fileName)
        if (!file.exists()) {
          suspend {
            val url = "$base/$modelType/${from.code}${to.code}/$fileName.gz"
            val success = downloadAndDecompress(url, file, to)
            Log.i("DownloadService", "Downloaded $url to $file = $success")
            success
          }
        } else {
          null
        }
      }
    return downloadJobs
  }

  private suspend fun downloadTessData(
    context: Context,
    language: Language,
  ): Boolean {
    val tessDataPath = FilePathManager(context).getTesseractDataDir()
    if (!tessDataPath.isDirectory) {
      tessDataPath.mkdirs()
    }
    val tessFile = File(tessDataPath, "${language.tessName}.traineddata")
    val url = "${settingsManager.settings.value.tesseractModelsBaseUrl}/${language.tessName}.traineddata"

    if (tessFile.exists()) {
      return true
    }
    return withContext(Dispatchers.IO) {
      val success = download(url, tessFile, language)
      Log.i(
        "DownloadService",
        "Downloaded tessdata for ${language.displayName} = $url to $tessFile: $success",
      )
      return@withContext success
    }
  }

  private suspend fun download(
    url: String,
    outputFile: File,
    language: Language,
    decompress: Boolean = false,
  ) = withContext(Dispatchers.IO) {
    val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")

    try {
      outputFile.parentFile?.mkdirs()

      val conn = URL(url).openConnection()
      val size = conn.contentLengthLong
      Log.i("DownloadService", "URL $url has size $size (${size / 1024 / 1024f}MB)")
      conn.getInputStream().use { rawInputStream ->
        val trackingStream =
          TrackingInputStream(rawInputStream, size) { incrementalProgress ->
            updateDownloadState(language) {
              val newProgress = it.progress + (incrementalProgress / it.taskCount.toFloat())
              it.copy(progress = newProgress)
            }
          }

        tempFile.outputStream().use { output ->
          val processedInput = if (decompress) GZIPInputStream(trackingStream) else trackingStream
          processedInput.use { stream ->
            val buffer = ByteArray(16384)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
              output.write(buffer, 0, bytesRead)
            }
          }
        }
      }

      if (tempFile.renameTo(outputFile)) {
        true
      } else {
        Log.e(
          "DownloadService",
          "Failed to move temp file $tempFile to final location $outputFile",
        )
        tempFile.delete()
        false
      }
    } catch (e: Exception) {
      val operation = if (decompress) "decompressing" else "downloading"
      Log.e("DownloadService", "Error $operation file", e)
      if (tempFile.exists()) {
        tempFile.delete()
      }
      if (outputFile.exists()) {
        outputFile.delete()
      }
      false
    }
  }

  private suspend fun downloadAndDecompress(
    url: String,
    outputFile: File,
    language: Language,
  ) = download(url, outputFile, language, decompress = true)

  private fun createNotificationChannel() {
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Language Downloads",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Shows progress of language pack downloads"
      }
    notificationManager.createNotificationChannel(channel)
  }

  private fun showNotification(
    title: String,
    content: String,
  ) {
    val notification =
      NotificationCompat
        .Builder(this, CHANNEL_ID)
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
    cleanupTempFiles()
  }

  private fun cleanupTempFiles() {
    val binDir = FilePathManager(this).getDataDir()
    if (binDir.exists()) {
      binDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { tempFile ->
        if (tempFile.delete()) {
          Log.d("DownloadService", "Cleaned up temp file: ${tempFile.name}")
        }
      }
    }

    val tessDir = FilePathManager(this).getTesseractDataDir()
    if (tessDir.exists()) {
      tessDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { tempFile ->
        if (tempFile.delete()) {
          Log.d("DownloadService", "Cleaned up temp file: ${tempFile.name}")
        }
      }
    }
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
  val taskCount: Int = 1,
  val error: String? = null,
)
