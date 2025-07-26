package dev.davidv.bergamot

class NativeLib {

    /**
     * A native method that is implemented by the 'bergamot' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(cfg: String, data: String, key: String): String
    
    /**
     * Cleanup method to properly dispose native resources
     */
    external fun cleanup()
    
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
    external fun detectLanguage(text: String): DetectionResult
}