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

package dev.davidv.translator.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import dev.davidv.translator.DocumentTranslationService
import dev.davidv.translator.DocumentTranslationServiceState
import dev.davidv.translator.FilePathManager
import dev.davidv.translator.Language
import dev.davidv.translator.PdfPhaseProgress
import dev.davidv.translator.R
import dev.davidv.translator.TxtLayoutChoice
import dev.davidv.translator.copyDocumentUriToCache
import dev.davidv.translator.displayNameForUri
import dev.davidv.translator.sizeBytesForUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PendingDocument(
  val path: String,
  val displayName: String,
  val sizeBytes: Long,
  val deleteAfterLoad: Boolean,
  val extension: String,
)

data class DocumentTranslationUiState(
  val taskId: Long,
  val fileName: String,
  val fileSizeBytes: Long,
  val outputPath: String? = null,
  val outputFileName: String? = null,
  val outputMimeType: String? = null,
  val errorMessage: String? = null,
  @StringRes val progressLabelRes: Int = R.string.doc_progress_preparing,
  val pdfPhases: PdfPhaseProgress? = null,
  val progressFraction: Float? = null,
) {
  val isTranslating: Boolean
    get() = outputPath == null && errorMessage == null
}

// The document translation runs in its own foreground service; while it does, the main
// screen shows nothing else, so starting and cancelling it are reported to the text session.
class DocumentSession(
  private val scope: CoroutineScope,
  private val appContext: Context,
  private val filePathManager: FilePathManager,
  private val onStarted: () -> Unit,
  private val onCancelled: () -> Unit,
) {
  val documentTranslation: StateFlow<DocumentTranslationUiState?> =
    DocumentTranslationService.documentTranslationState
      .map { it?.toUiState() }
      .stateIn(scope, SharingStarted.Eagerly, DocumentTranslationService.documentTranslationState.value?.toUiState())

  // Picked-but-not-yet-started document. Drives the Configure sheet
  // (source/dest + per-type options) before the translation service is
  // launched; cleared once the user confirms or cancels.
  private val _pendingDocument = MutableStateFlow<PendingDocument?>(null)
  val pendingDocument: StateFlow<PendingDocument?> = _pendingDocument.asStateFlow()

  fun offer(
    path: String,
    displayName: String,
    sizeBytes: Long,
    deleteAfterLoad: Boolean,
  ) {
    Log.d("SetDocumentPath", "Selected document for translation: $displayName ($path)")
    _pendingDocument.value =
      PendingDocument(
        path = path,
        displayName = displayName,
        sizeBytes = sizeBytes,
        deleteAfterLoad = deleteAfterLoad,
        extension = File(path).extension.lowercase(),
      )
  }

  // A shared document (pdf/odt/txt/epub) reaches the same drawer as a picked one:
  // copy the content URI into cache off the main thread, then drive the pending
  // document state the file picker also feeds.
  fun importShared(uri: Uri) {
    scope.launch {
      try {
        val file = withContext(Dispatchers.IO) { copyDocumentUriToCache(appContext, uri) }
        offer(
          path = file.absolutePath,
          displayName = displayNameForUri(appContext, uri) ?: file.name,
          sizeBytes = sizeBytesForUri(appContext, uri) ?: file.length(),
          deleteAfterLoad = true,
        )
      } catch (e: Exception) {
        Log.e("SharedDocument", "Failed to import shared document: $uri", e)
      }
    }
  }

  fun dismissPending() {
    val pending = _pendingDocument.getAndUpdate { null } ?: return
    if (pending.deleteAfterLoad) {
      runCatching { File(pending.path).delete() }
    }
  }

  fun start(
    from: Language,
    to: Language,
    txtLayout: TxtLayoutChoice,
    translatePdfImages: Boolean,
  ) {
    // A second confirm can land before the sheet leaves the screen.
    val pending = _pendingDocument.getAndUpdate { null } ?: return
    onStarted()
    val outputName = translatedDocumentFileName(pending.displayName, from, to)
    DocumentTranslationService.startTranslation(
      context = appContext,
      inputPath = pending.path,
      outputPath = File(filePathManager.getTranslatedDocumentsDir(), outputName).absolutePath,
      displayName = pending.displayName,
      sizeBytes = pending.sizeBytes,
      from = from,
      to = to,
      deleteAfterLoad = pending.deleteAfterLoad,
      translatePdfImages = translatePdfImages,
      txtLayout = txtLayout,
    )
  }

  fun cancel() {
    DocumentTranslationService.cancel(appContext)
    onCancelled()
  }

  fun dismissResult() {
    DocumentTranslationService.dismiss(appContext)
  }
}

internal fun translatedDocumentFileName(
  inputName: String,
  from: Language,
  to: Language,
): String {
  val inputFile = File(inputName)
  val extension = inputFile.extension.ifBlank { "txt" }
  val baseName = inputFile.nameWithoutExtension.ifBlank { "document" }
  val safeBaseName = baseName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "document" }
  return "$safeBaseName.${from.code}-${to.code}.$extension"
}

private fun DocumentTranslationServiceState.toUiState(): DocumentTranslationUiState =
  DocumentTranslationUiState(
    taskId = taskId,
    fileName = fileName,
    fileSizeBytes = fileSizeBytes,
    outputPath = outputPath,
    outputFileName = outputPath?.let { File(it).name },
    outputMimeType = outputPath?.let { mimeTypeForDocumentPath(it) },
    errorMessage = errorMessage,
    progressLabelRes = progressLabelRes,
    pdfPhases = pdfPhases,
    progressFraction = progressFraction,
  )

private fun mimeTypeForDocumentPath(path: String): String =
  when (File(path).extension.lowercase()) {
    "pdf" -> "application/pdf"
    "odt" -> "application/vnd.oasis.opendocument.text"
    "epub" -> "application/epub+zip"
    "txt" -> "text/plain"
    else -> "application/octet-stream"
  }
