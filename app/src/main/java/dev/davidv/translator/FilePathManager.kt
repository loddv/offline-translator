package dev.davidv.translator

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class FilePathManager(
  private val context: Context,
  private val settingsFlow: StateFlow<AppSettings>,
) {
  private val baseDir: File
    get() =
      if (settingsFlow.value.useExternalStorage) {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        File(documentsDir, "dev.davidv.translator").also { dir ->
          if (!dir.exists()) {
            dir.mkdirs()
          }
        }
      } else {
        context.filesDir
      }

  fun getDataDir(): File = File(baseDir, "bin")

  fun getTesseractDataDir(): File = File(baseDir, "tesseract/tessdata")

  fun getTesseractDir(): File = File(baseDir, "tesseract")

  fun getDictionaryFile(language: Language): File = File(getDataDir(), "${language.code}.dict")
}
