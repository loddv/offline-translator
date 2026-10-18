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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import uniffi.bindings.planZipExtraction
import uniffi.translator_core.InstallMarker
import uniffi.translator_core.TaskInstall
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.math.max

private const val TAG = "PackInstaller"
private const val COPY_BUFFER_BYTES = 16 * 1024
private const val MIN_PROGRESS_STEP_BYTES = 128L * 1024
private const val STAGING_DIR = ".staging"

private enum class Encoding {
  IDENTITY,
  GZIP,
}

// A plan's files land here first and move into the install root together under the catalog's
// publish lock, so a rescan never sees a pack with only some of its files.
class StagedInstall(
  private val root: File,
  private val filePathManager: FilePathManager,
) {
  private val files = ConcurrentLinkedQueue<String>()
  private val directories = ConcurrentLinkedQueue<String>()
  private val replacedPaths = ConcurrentLinkedQueue<String>()
  private val markers = ConcurrentLinkedQueue<InstallMarker>()

  fun stagedFile(installPath: String): File = File(root, installPath)

  fun keepFile(installPath: String) = files.add(installPath)

  fun keepDirectory(installPath: String) = directories.add(installPath)

  fun replacePath(installPath: String) = replacedPaths.add(installPath)

  fun writeMarker(marker: InstallMarker) = markers.add(marker)

  // Markers go last: an archive only reads as installed once every extracted file is in place.
  fun moveIntoPlace() {
    replacedPaths.forEach { filePathManager.resolveInstallPath(it).deleteRecursively() }
    directories.forEach { filePathManager.resolveInstallPath(it).mkdirs() }
    files.forEach { path ->
      val target = filePathManager.resolveInstallPath(path)
      target.parentFile?.mkdirs()
      if (!stagedFile(path).renameTo(target)) {
        throw IOException("Failed to move staged $path to $target")
      }
    }
    markers.forEach { filePathManager.writeInstallMarker(it.path, it.version) }
  }

  fun discard() {
    root.deleteRecursively()
  }
}

class PackInstaller(
  private val filePathManager: FilePathManager,
  private val catalogRepository: CatalogRepository,
) {
  // Plans can share files (every TTS voice depends on the espeak pack). Each file is fetched by
  // the first in-flight run that needs it; later runs wait for that run to finish and then find
  // it installed. Waits only ever point at earlier runs, so they can't form a cycle.
  private val fileOwners = mutableMapOf<String, Job>()

  init {
    // Staging left behind by a killed process is never committed.
    File(filePathManager.currentBaseDir(), STAGING_DIR).deleteRecursively()
  }

  // The whole plan commits at once: the catalog reads a language with one direction installed and
  // the other still missing files as needing repair, so packs can't land one by one.
  suspend fun installPlan(
    plan: DownloadPlan,
    onProgress: (Long) -> Unit,
  ) {
    val done = Job()
    val earlierOwners =
      synchronized(fileOwners) {
        plan.tasks.associateWith { task -> fileOwners.putIfAbsent(task.installPath, done) }
      }
    val staging =
      StagedInstall(File(File(filePathManager.currentBaseDir(), STAGING_DIR), UUID.randomUUID().toString()), filePathManager)
    try {
      coroutineScope {
        earlierOwners
          .map { (task, owner) ->
            async {
              // join rather than await: the owner failing or being cancelled must not end this run;
              // install then fetches the file itself.
              owner?.join()
              install(task, staging, onProgress)
            }
          }.awaitAll()
      }
      catalogRepository.commitInstall(staging, plan.deleteAfter)
    } finally {
      synchronized(fileOwners) { fileOwners.values.removeAll { it === done } }
      done.complete()
      staging.discard()
    }
  }

  // Reports deltas through onProgress, adding up to exactly task.sizeBytes once the task lands.
  private suspend fun install(
    task: DownloadTask,
    staging: StagedInstall,
    onProgress: (Long) -> Unit,
  ) {
    val expected = task.sizeBytes.toLong()
    if (catalogRepository.catalog.value.downloadTaskInstalled(task)) {
      Log.i(TAG, "Reusing installed ${task.packId}:${task.installPath}")
      onProgress(expected)
      return
    }
    val target = staging.stagedFile(task.installPath)
    val reported =
      when (val install = task.install) {
        TaskInstall.Raw -> download(task.url, target, Encoding.IDENTITY, expected, onProgress)
        TaskInstall.Gunzip -> download(task.url, target, Encoding.GZIP, expected, onProgress)
        is TaskInstall.Zip -> {
          val reported = download(task.url, target, Encoding.IDENTITY, expected, onProgress)
          extractZip(target, install, staging)
          reported
        }
      }
    val install = task.install
    if (install !is TaskInstall.Zip || !install.deleteAfterExtract) {
      staging.keepFile(task.installPath)
    }
    onProgress(max(0L, expected - reported))
    Log.i(TAG, "Staged ${task.packId}:${task.installPath} from ${task.url}")
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
    staging: StagedInstall,
  ) = withContext(Dispatchers.IO) {
    val entryNames = ZipFile(archive).use { file -> file.entries().asSequence().map { it.name }.toList() }
    val plan = planZipExtraction(zip.extractTo, zip.marker, entryNames)
    plan.replacePaths.forEach(staging::replacePath)
    val targets = plan.entries.associateBy { it.entryName }
    ZipInputStream(archive.inputStream().buffered()).use { input ->
      var entry = input.nextEntry
      while (entry != null) {
        ensureActive()
        val target = targets[entry.name]
        when {
          target == null -> Unit
          target.isDirectory -> staging.keepDirectory(target.installPath)
          else -> {
            val output = staging.stagedFile(target.installPath)
            output.parentFile?.mkdirs()
            output.outputStream().use { input.copyTo(it, COPY_BUFFER_BYTES) }
            staging.keepFile(target.installPath)
          }
        }
        entry = input.nextEntry
      }
    }
    zip.marker?.let(staging::writeMarker)
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
