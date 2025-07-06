package com.example.translator

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
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
    private val context: Context,
    private val onProgress: (Float) -> Unit = {}
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
                Log.e("OCRService", "Failed to initialize Tesseract with languages: $availableLanguages")
                return@withContext false
            }

            isInitialized = true
            Log.i("OCRService", "Tesseract initialized successfully with languages: $availableLanguages")
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

        try {
            val tessInstance = tess ?: return@withContext ""
            
            tessInstance.setImage(bitmap)
            tessInstance.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD)

            val text: String
            val elapsed = measureTimeMillis {
                tessInstance.getHOCRText(0)
                text = tessInstance.getConfidentText(80, TessBaseAPI.PageIteratorLevel.RIL_WORD)
            }
            
            Log.i("OCRService", "OCR took ${elapsed}ms")
            text
        } catch (e: Exception) {
            Log.e("OCRService", "Error extracting text", e)
            ""
        }
    }


    fun cleanup() {
        tess?.recycle()
        tess = null
        isInitialized = false
        Log.i("OCRService", "OCR service cleaned up")
    }
}

