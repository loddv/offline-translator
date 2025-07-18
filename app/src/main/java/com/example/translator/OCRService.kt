package com.example.translator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_PARA
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel.RIL_WORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    var lastRight = 0
    do {
        val word = iter.getUTF8Text(RIL_WORD)
        if (word == null) {
            Log.e("OCRService", "WTF word was null")
            continue
        }
        if (word.trim() == "") {
            continue
        }

        val conf = iter.confidence(RIL_WORD)
        if (conf < minConfidence) continue
        if (word.length == 1 && conf < min(100, minConfidence + 5)) continue
        val boundingBox = iter.getBoundingRect(RIL_WORD)

        val firstWordInLine = iter.isAtBeginningOf(RIL_TEXTLINE)
        val lastWordInLine = iter.isAtFinalElement(RIL_TEXTLINE, RIL_WORD)
        val lastWordInPara = iter.isAtFinalElement(RIL_PARA, RIL_WORD)

        if (firstWordInLine) {
            line = TextLine(word, boundingBox)
        } else {
            val delta = boundingBox.left - lastRight
            val charWidth = boundingBox.width() / word.length
            val deltaInChars = delta / charWidth

            // In the same line but too far apart, make a new block
            if (deltaInChars >= 3) { // TODO: how to figure out the delta better
                println("Forcing new block with word $word")
                if (line.text.trim() != "") {
                    lines.add(line)
                }
                line = TextLine(word, boundingBox)
                if (lines.isNotEmpty()) {
                    blocks.add(TextBlock(lines.toTypedArray()))
                    lines = mutableListOf()
                }
            } else {
                line.text = "${line.text} ${word}"
                line.boundingBox = Rect(
                    // left is immutable
                    line.boundingBox.left,
                    min(
                        line.boundingBox.top,
                        boundingBox.top
                    ), // top can be stretched upwards ('g' -> 'T')
                    boundingBox.right, // Right takes the right of the new word always
                    max(
                        line.boundingBox.bottom,
                        boundingBox.bottom
                    ) // bottom can be stretched ('a' -> 'g')
                )
            }
        }

        if (lastWordInLine && line.text.trim() != "") {
            lines.add(line)
            line = TextLine("", Rect(0, 0, 0, 0))
        }

        if (lastWordInPara && lines.isNotEmpty()) {
            blocks.add(TextBlock(lines.toTypedArray()))
            lines = mutableListOf()
        }
        lastRight = boundingBox.right

    } while (iter.next(RIL_WORD))

    iter.delete()
    tessInstance.clear()

    return blocks.toTypedArray()
}

class OCRService(
    private val context: Context
) {
    private var tess: TessBaseAPI? = null
    private var isInitialized = false

    private suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
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

            tess = TessBaseAPI()

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

    suspend fun extractText(bitmap: Bitmap, minConfidence: Int = 75): Array<TextBlock> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            val initSuccess = initialize()
            if (!initSuccess) return@withContext emptyArray()
        }

        val tessInstance = tess ?: return@withContext emptyArray()

        val blocks: Array<TextBlock>
        val elapsed = measureTimeMillis {
            blocks = getSentences(bitmap, tessInstance, minConfidence)
        }
        // Release image data & results; but keeps the instance active
        Log.i("OCRService", "OCR took ${elapsed}ms")
        blocks

    }


    fun cleanup() {
        tess?.recycle()
        tess = null
        isInitialized = false
        Log.i("OCRService", "OCR service cleaned up")
    }
}

