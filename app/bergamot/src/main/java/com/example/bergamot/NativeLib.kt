package com.example.bergamot

import android.os.Debug

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