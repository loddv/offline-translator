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

package dev.davidv.translator

import android.icu.text.Transliterator
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

object TransliterationService {
    
    private val transliterators = mutableMapOf<String, Transliterator?>()
    
    private val cjkLanguages = setOf(
        Language.CHINESE,
        Language.JAPANESE,
        Language.KOREAN
    )
    
    private val transliterationRules = mapOf(
        Language.CHINESE to "Han-Latin",
        Language.JAPANESE to "Hiragana-Latin; Katakana-Latin; Han-Latin",
        Language.KOREAN to "Hangul-Latin"
    )
    
    private fun shouldTransliterate(language: Language): Boolean {
        return cjkLanguages.contains(language)
    }
    
    fun transliterate(text: String, language: Language): String? {
        if (!shouldTransliterate(language) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        
        val rule = transliterationRules[language] ?: return null
        
        return try {
            val transliterator = getTransliterator(rule)
            transliterator?.transliterate(text)
        } catch (e: Exception) {
            Log.w("TransliterationService", "Failed to transliterate text for $language", e)
            null
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getTransliterator(rule: String): Transliterator? {
        return transliterators.getOrPut(rule) {
            try {
                Transliterator.getInstance(rule)
            } catch (e: Exception) {
                Log.w("TransliterationService", "Failed to create transliterator for rule: $rule", e)
                null
            }
        }
    }

}