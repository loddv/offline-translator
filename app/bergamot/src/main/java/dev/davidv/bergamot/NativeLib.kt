/*
 * Copyright (C) 2024 David V
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.davidv.bergamot

class NativeLib() {
    init {
        initializeService()
    }

    external fun stringFromJNI(cfg: String, data: String, key: String): String
    external fun loadModelIntoCache(cfg: String, key: String)
    private external fun initializeService()

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