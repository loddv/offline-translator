package com.example.translator

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.googlecode.leptonica.android.ReadFile
import com.googlecode.tesseract.android.TessBaseAPI

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.translator", appContext.packageName)
    }

    @Test
    fun extractSentences() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext


        context.assets.list("").let { item -> println("xxitem ${item.contentToString()}") }
        // Load PNG from assets
        val inputStream = context.assets.open("screen.png")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val tessDir = File(appContext.dataDir, "tesseract")
        val tessdataDir = File(tessDir, "tessdata")
        val dataPath: String = tessDir.toPath().absolutePathString()
        tessdataDir.mkdirs()

        // copy from test context to app context
        val inputStreamEng = context.assets.open("eng.traineddata")
        val trainedDataFile = File(tessdataDir, "eng.traineddata")
        trainedDataFile.outputStream().use { output ->
            inputStreamEng.copyTo(output)
        }
        inputStreamEng.close()

        
        val tessInstance = TessBaseAPI()
        assert(tessInstance.init(dataPath, "eng"))
        val blocks = getSentences(bitmap, tessInstance)
        println(blocks.joinToString("\n"))
        assertEquals(8, blocks.count()) // first 3 contain some artifact
        assertEquals(3, blocks[3].lines.count())
        assertEquals("Philipsen sprint naar eerste gele trui in", blocks[3].lines[0].text)
        assertEquals("Tour, Evenepoel mist slag in hectische", blocks[3].lines[1].text)
        assertEquals("finale", blocks[3].lines[2].text)
    }
}