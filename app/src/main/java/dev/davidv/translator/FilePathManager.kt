package dev.davidv.translator

import android.content.Context
import java.io.File

class FilePathManager(private val context: Context) {
  fun getDataDir(): File {
    return File(context.filesDir, "bin")
  }

  fun getTesseractDataDir(): File {
    return File(context.filesDir, "tesseract/tessdata")
  }

  fun getTesseractDir(): File {
    return File(context.filesDir, "tesseract")
  }
}
