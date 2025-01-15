package com.example.bergamot

class NativeLib {

    /**
     * A native method that is implemented by the 'bergamot' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(cfg: String, data: String): String
    companion object {
        // Used to load the 'bergamot' library on application startup.
        init {
//            Debug.waitForDebugger()
            System.loadLibrary("bergamot-sys")
        }
    }
}


data class DetectionResult(
    val language: String,
    val isReliable: Boolean,
    val confidence: Int
)

class LangDetect {
    companion object {
        init {
            println("Loaded cld2")
            System.loadLibrary("bergamot-sys")
        }
    }

    external fun detectLanguage(text: String): DetectionResult
}