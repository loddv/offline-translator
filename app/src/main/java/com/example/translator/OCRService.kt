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
            // Setup Tesseract directories
            val p = File(context.filesDir, "tesseract").toPath()
            val tessdata = Path(p.pathString, "tessdata")
            val dataPath: String = p.absolutePathString()
            tessdata.createDirectories()

            // Download language data if needed
            val lang = "nld"
            val outputFile = File(Path(tessdata.absolutePathString(), "$lang.traineddata").absolutePathString())
            if (!outputFile.exists()) {
                Log.w("OCRService", "Language data not found: $outputFile")
                // Could download here if needed
                return@withContext false
            }

            // Initialize TessBaseAPI
            tess = TessBaseAPI { progress ->
                onProgress(progress.percent / 100f)
            }

            val initialized = tess?.init(dataPath, lang) ?: false
            if (!initialized) {
                tess?.recycle()
                tess = null
                Log.e("OCRService", "Failed to initialize Tesseract")
                return@withContext false
            }

            isInitialized = true
            Log.i("OCRService", "Tesseract initialized successfully")
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

    suspend fun downloadLanguageData(lang: String = "nld"): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = File(context.filesDir, "tesseract").toPath()
            val tessdata = Path(p.pathString, "tessdata")
            tessdata.createDirectories()
            
            val outputFile = File(Path(tessdata.absolutePathString(), "$lang.traineddata").absolutePathString())
            if (outputFile.exists()) {
                Log.i("OCRService", "Language data already exists")
                return@withContext true
            }

            val url = "https://github.com/tesseract-ocr/tessdata_fast/raw/refs/heads/main/$lang.traineddata"
            val success = download(url, outputFile)
            Log.i("OCRService", "Downloaded language data: $success")
            success
        } catch (e: Exception) {
            Log.e("OCRService", "Error downloading language data", e)
            false
        }
    }

    fun cleanup() {
        tess?.recycle()
        tess = null
        isInitialized = false
        Log.i("OCRService", "OCR service cleaned up")
    }
}

suspend fun download(url: String, outputFile: File) = withContext(Dispatchers.IO) {
    try {
        URL(url).openStream().use { input ->
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        true
    } catch (e: Exception) {
        Log.e("Download", "Error downloading file", e)
        false
    }
}