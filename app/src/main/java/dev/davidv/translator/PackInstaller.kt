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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.bindings.planZipExtraction
import uniffi.translator_core.TaskInstall
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.max

private const val TAG = "PackInstaller"
private const val COPY_BUFFER_BYTES = 16 * 1024
private const val MIN_PROGRESS_STEP_BYTES = 128L * 1024

private enum class Encoding {
  IDENTITY,
  GZIP,
}

class PackInstaller(
  private val filePathManager: FilePathManager,
  private val catalogRepository: CatalogRepository,
) {
  private val fileLocks = ConcurrentHashMap<String, Mutex>()

  // Reports deltas through onProgress, adding up to exactly task.sizeBytes once the task lands.
  suspend fun install(
    task: DownloadTask,
    onProgress: (Long) -> Unit,
  ) {
    val target = filePathManager.resolveInstallPath(task.installPath)
    val expected = task.sizeBytes.toLong()
    // Concurrent plans can share a file; the lock makes the later one find it installed.
    fileLocks.computeIfAbsent(target.absolutePath) { Mutex() }.withLock {
      if (catalogRepository.catalog.value.downloadTaskInstalled(task)) {
        Log.i(TAG, "Reusing installed ${task.packId}:${task.installPath}")
        onProgress(expected)
        return
      }
      val reported =
        when (val install = task.install) {
          TaskInstall.Raw -> download(task.url, target, Encoding.IDENTITY, expected, onProgress)
          TaskInstall.Gunzip -> download(task.url, target, Encoding.GZIP, expected, onProgress)
          is TaskInstall.Zip -> {
            val reported = download(task.url, target, Encoding.IDENTITY, expected, onProgress)
            extractZip(target, install)
            reported
          }
        }
      onProgress(max(0L, expected - reported))
      Log.i(TAG, "Installed ${task.packId}:${task.installPath} from ${task.url}")
    }
  }

  suspend fun downloadFile(
    url: String,
    target: File,
  ) {
    download(url, target, Encoding.IDENTITY, expectedBytes = 0L) {}
  }

  // Returns the bytes reported through onProgress, capped at expectedBytes when it is known.
  private suspend fun download(
    url: String,
    target: File,
    encoding: Encoding,
    expectedBytes: Long,
    onProgress: (Long) -> Unit,
  ): Long =
    withContext(Dispatchers.IO) {
      target.parentFile?.mkdirs()
      val tempFile = File(target.parentFile, "${target.name}.tmp")
      try {
        val connection = URL(url).openConnection()
        val progress = ProgressReporter(expectedBytes.takeIf { it > 0 } ?: connection.contentLengthLong, onProgress)
        connection.getInputStream().use { raw ->
          val counted = CountingInputStream(raw, progress::advance)
          val decoded = if (encoding == Encoding.GZIP) GZIPInputStream(counted) else counted
          decoded.use { input ->
            tempFile.outputStream().use { output ->
              val buffer = ByteArray(COPY_BUFFER_BYTES)
              while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
              }
            }
          }
        }
        progress.flush()
        if (!tempFile.renameTo(target)) {
          throw IOException("Failed to move $tempFile to $target")
        }
        progress.reported
      } finally {
        tempFile.delete()
      }
    }

  private suspend fun extractZip(
    archive: File,
    zip: TaskInstall.Zip,
  ) = withContext(Dispatchers.IO) {
    try {
      val entryNames = ZipFile(archive).use { file -> file.entries().asSequence().map { it.name }.toList() }
      val plan = planZipExtraction(zip.extractTo, zip.marker, entryNames)
      plan.replacePaths.forEach { filePathManager.resolveInstallPath(it).deleteRecursively() }
      val targets = plan.entries.associateBy { it.entryName }
      ZipInputStream(archive.inputStream().buffered()).use { input ->
        var entry = input.nextEntry
        while (entry != null) {
          ensureActive()
          val target = targets[entry.name]
          when {
            target == null -> Unit
            target.isDirectory -> filePathManager.resolveInstallPath(target.installPath).mkdirs()
            else -> {
              val output = filePathManager.resolveInstallPath(target.installPath)
              output.parentFile?.mkdirs()
              output.outputStream().use { input.copyTo(it, COPY_BUFFER_BYTES) }
            }
          }
          entry = input.nextEntry
        }
      }
    } catch (e: Exception) {
      // A half-extracted archive must be fetched again, never reused.
      archive.delete()
      throw e
    }
    zip.marker?.let { filePathManager.writeInstallMarker(it.path, it.version) }
    if (zip.deleteAfterExtract) {
      archive.delete()
    }
  }
}

private class ProgressReporter(
  private val expectedBytes: Long,
  private val onProgress: (Long) -> Unit,
) {
  private val step = max(MIN_PROGRESS_STEP_BYTES, expectedBytes / 20)
  private var read = 0L
  var reported = 0L
    private set

  fun advance(bytes: Int) {
    read += bytes
    if (read - reported > step) flush()
  }

  fun flush() {
    val target = if (expectedBytes > 0) minOf(read, expectedBytes) else read
    if (target <= reported) return
    onProgress(target - reported)
    reported = target
  }
}

private class CountingInputStream(
  private val inputStream: InputStream,
  private val onRead: (Int) -> Unit,
) : InputStream() {
  override fun read(): Int {
    val byte = inputStream.read()
    if (byte != -1) onRead(1)
    return byte
  }

  override fun read(
    b: ByteArray,
    off: Int,
    len: Int,
  ): Int {
    val bytesRead = inputStream.read(b, off, len)
    if (bytesRead > 0) onRead(bytesRead)
    return bytesRead
  }

  override fun close() = inputStream.close()
}
