package com.example.translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.system.measureTimeMillis

class OCRService(
    private val context: Context, private val onProgress: (Float) -> Unit = {}
) {
    private var tess: TessBaseAPI? = null
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            val p = File(context.filesDir, "tesseract").toPath()
            val tessdata = Path(p.pathString, "tessdata")
            val dataPath: String = p.absolutePathString()
            tessdata.createDirectories()

            // Get available language data
            val availableLanguages = getAvailableTessLanguages(context)
            if (availableLanguages.isEmpty()) {
                Log.w("OCRService", "No tessdata language files found")
                return@withContext false
            }

            tess = TessBaseAPI { progress ->
                onProgress(progress.percent / 100f * 1.46f) // tesseract reports up to 66%?
            }

            val initialized = tess?.init(dataPath, availableLanguages) ?: false
            if (!initialized) {
                tess?.recycle()
                tess = null
                Log.e(
                    "OCRService",
                    "Failed to initialize Tesseract with languages: $availableLanguages"
                )
                return@withContext false
            }

            isInitialized = true
            Log.i(
                "OCRService",
                "Tesseract initialized successfully with languages: $availableLanguages"
            )
            true
        } catch (e: Exception) {
            Log.e("OCRService", "Error initializing Tesseract", e)
            false
        }
    }

    suspend fun extractText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            val initSuccess = initialize()
            if (!initSuccess) return@withContext ""
        }

        val tessInstance = tess ?: return@withContext ""

        tessInstance.setImage(bitmap)
        tessInstance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD)

        var text = ""
        var sentences = arrayOf(String)
        val elapsed = measureTimeMillis {
            tessInstance.getHOCRText(0)

            // TODO: repaint on top of the image.
            // Need to get background color (how?) and foreground color (how?)
            // maybe tessInstance.thresholdedImage to find the position of the letter
            // then pick a pixel from the letter??
            // Also, read getConfidentText implementation to do this
            //https://github.com/tesseract-ocr/tesseract/blob/d8d63fd71b8d56f73469f7db41864098f087599c/src/api/hocrrenderer.cpp#L190
            if (false) {
                val iter = tessInstance.resultIterator
                iter.begin()
                do {
                    val conf = iter.confidence(RIL_WORD)
                    val word = iter.getUTF8Text(RIL_WORD)
                    val boundingBox = iter.getBoundingRect(RIL_WORD)
                    println("$word ($conf) x ${boundingBox.centerX()} y: ${boundingBox.centerY()} h: ${boundingBox.height()}")
                    if (conf < 80) continue
                    val isLastWord =
                        iter.isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE, RIL_WORD)
                    // val isLastWord = prevRect != null && boundingBox.centerY() > (prevRect.centerY() + (prevRect.height() / 2))
                    text += word
                    if (isLastWord) {
                        text += "[NL]\n"
                    } else {
                        text += " "
                    }
                } while (iter.next(RIL_WORD))
            }
            text = tessInstance.getConfidentText(80, RIL_WORD)
        }

        Log.i("OCRService", "OCR took ${elapsed}ms")
        text

    }


    fun cleanup() {
        tess?.recycle()
        tess = null
        isInitialized = false
        Log.i("OCRService", "OCR service cleaned up")
    }
}

