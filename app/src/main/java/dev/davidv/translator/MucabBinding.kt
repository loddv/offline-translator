package dev.davidv.translator

class MucabBinding {
  companion object {
    init {
      System.loadLibrary("bindings")
    }
  }

  private var dictPtr: Long = 0

  fun open(path: String): Boolean {
    close()
    dictPtr = nativeOpen(path)
    return dictPtr != 0L
  }

  fun transliterateJP(text: String): String? =
    if (dictPtr != 0L) {
      nativeTransliterateJP(dictPtr, text)
    } else {
      null
    }

  fun close() {
    if (dictPtr != 0L) {
      nativeClose(dictPtr)
      dictPtr = 0
    }
  }

  fun isOpen(): Boolean = dictPtr != 0L

  private external fun nativeOpen(path: String): Long

  private external fun nativeTransliterateJP(
    dictPtr: Long,
    text: String,
  ): String?

  private external fun nativeClose(dictPtr: Long)

  protected fun finalize() {
    close()
  }
}
