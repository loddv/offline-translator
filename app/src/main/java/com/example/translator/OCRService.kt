package com.example.translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.compose.material3.Text
import com.googlecode.leptonica.android.Box
import com.googlecode.leptonica.android.Pix
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
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

data class TextLine(var text: String, var boundingBox: Rect)
data class TextBlock(val lines: Array<TextLine>)

fun getSentences(bitmap: Bitmap, tessInstance: TessBaseAPI): Array<TextBlock> {
    tessInstance.setImage(bitmap)
    tessInstance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD)


    tessInstance.getHOCRText(0)

    val iter = tessInstance.resultIterator
    if (iter == null) {
        println("NULL ITERATOR")
        return emptyArray()
    }

    iter.begin()
    val blocks = mutableListOf<TextBlock>()
    var lines = mutableListOf<TextLine>()
    var line = TextLine("", Rect(0, 0, 0, 0))
    do {
        val conf = iter.confidence(RIL_WORD)
        val word = iter.getUTF8Text(RIL_WORD)
        val boundingBox = iter.getBoundingRect(RIL_WORD)
        println("$word ($conf) l ${boundingBox.left} t: ${boundingBox.top} r: ${boundingBox.right} b ${boundingBox.bottom}")
        if (conf < 80) continue
        val firstWordInLine = iter.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)
        val lastWordInLine =
            iter.isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE, RIL_WORD)
        val lastWordInPara = iter.isAtFinalElement(TessBaseAPI.PageIteratorLevel.RIL_PARA, RIL_WORD)

        if (firstWordInLine) {
            line = TextLine(word, boundingBox);
        } else {
            line.text = "${line.text} ${word}"
            line.boundingBox = Rect(
                line.boundingBox.left, // left is immutable
                min(line.boundingBox.top, boundingBox.top), // top can be stretched upwards ('g' -> 'T')
                boundingBox.right, // Right takes the right of the new word always
                max(line.boundingBox.bottom, boundingBox.bottom) // bottom can be stretched ('a' -> 'g')
            )
        }

        if (lastWordInLine && line.text != " ") {
            lines.add(line)
            line = TextLine("", Rect(0, 0, 0, 0))
        }

        if (lastWordInPara && lines.isNotEmpty()) {
            blocks.add(TextBlock(lines.toTypedArray()))
            lines = mutableListOf()
        }

    } while (iter.next(RIL_WORD))


    return blocks.toTypedArray()

}

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
        val elapsed = measureTimeMillis {
            tessInstance.getHOCRText(0)

            // TODO: repaint on top of the image.
            // Need to get background color (how?) and foreground color (how?)
            // maybe tessInstance.thresholdedImage to find the position of the letter
            // then pick a pixel from the letter??
            // Also, read getConfidentText implementation to do this
            //https://github.com/tesseract-ocr/tesseract/blob/d8d63fd71b8d56f73469f7db41864098f087599c/src/api/hocrrenderer.cpp#L190
            if (false) {
                getSentences(bitmap, tessInstance)
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

