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

import android.content.Context
import android.util.Log
import dev.davidv.bergamot.NativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis

class TranslationService(private val context: Context) {
    
    companion object {
        @Volatile
        private var nativeLibInstance: NativeLib? = null
        
        private fun getNativeLib(): NativeLib {
            return nativeLibInstance ?: synchronized(this) {
                nativeLibInstance ?: NativeLib().also { nativeLibInstance = it }
            }
        }
        
        fun cleanup() {
            synchronized(this) {
                nativeLibInstance?.cleanup()
                nativeLibInstance = null
            }
        }
    }
    
    private val nativeLib = getNativeLib()
    
    suspend fun preloadModel(from: Language, to: Language) = withContext(Dispatchers.IO) {
        try {
            val translationPairs = getTranslationPairs(from, to)
            
            for (pair in translationPairs) {
                if (checkLanguagePairFiles(context, pair.first, pair.second)) {
                    val config = generateConfig(pair.first, pair.second)
                    val languageCode = "${pair.first.code}${pair.second.code}"
                    Log.d("TranslationService", "Preloading model with key: $languageCode")
//                    nativeLib.loadModelIntoCache(config, languageCode)
                    nativeLib.stringFromJNI(config, ".", languageCode) // translate empty string to load the model
                    Log.d("TranslationService", "Preloaded model for ${pair.first} -> ${pair.second} with key: $languageCode")
                }
            }
        } catch (e: Exception) {
            Log.w("TranslationService", "Failed to preload model for $from -> $to", e)
        }
    }
    
    suspend fun translate(
        from: Language,
        to: Language,
        text: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        if (from == to) {
            return@withContext TranslationResult.Success(text)
        }
        // numbers don't translate :^)
        if (text.trim().toFloatOrNull() != null) {
            return@withContext TranslationResult.Success(text)
        }

        if (text.isBlank()) {
            return@withContext TranslationResult.Success("")
        }

        Log.d("TranslationService", "Translating $from -> $to")

        try {
            val translationPairs = getTranslationPairs(from, to)
            
            // Validate all required language pairs are available
            for (pair in translationPairs) {
                if (!checkLanguagePairFiles(context, pair.first, pair.second)) {
                    return@withContext TranslationResult.Error("Language pair ${pair.first} -> ${pair.second} not installed")
                }
            }
            
            val result = performTranslation(translationPairs, text)
            TranslationResult.Success(result)
            
        } catch (e: Exception) {
            Log.e("TranslationService", "Translation failed", e)
            TranslationResult.Error("Translation failed: ${e.message}")
        }
    }
    
    private fun getTranslationPairs(from: Language, to: Language): List<Pair<Language, Language>> {
        return when {
            from == Language.ENGLISH && to == Language.ENGLISH -> emptyList()
            from == Language.ENGLISH -> listOf(from to to)
            to == Language.ENGLISH -> listOf(from to to)
            else -> listOf(from to Language.ENGLISH, Language.ENGLISH to to) // Pivot through English
        }
    }
    
    private fun performTranslation(pairs: List<Pair<Language, Language>>, initialText: String): String {
        var currentText = initialText
        val totalElapsed = measureTimeMillis {
            pairs.forEach { pair ->
                Log.d("TranslationService", "Translating step: $pair")
                val stepElapsed = measureTimeMillis {
                    val config = generateConfig(pair.first, pair.second)
                    val languageCode = "${pair.first.code}${pair.second.code}"
                    Log.d("TranslationService", "Using model key: $languageCode for translation")
                    currentText = nativeLib.stringFromJNI(config, currentText, languageCode)
                }
                Log.d("TranslationService", "Step ${pair.first} -> ${pair.second} took ${stepElapsed}ms")
            }
        }
        Log.d("TranslationService", "Total translation took ${totalElapsed}ms")
        return currentText
    }
    
    private fun generateConfig(fromLang: Language, toLang: Language): String {
        val dataPath = File(context.filesDir, "bin")
        val (model, vocab, lex) = filesFor(fromLang, toLang)
        return """
models:
  - ${dataPath}/${model}
vocabs:
  - ${dataPath}/${vocab}
  - ${dataPath}/${vocab}
shortlist:
    - ${dataPath}/${lex}
    - false
beam-size: 1
normalize: 1.0
word-penalty: 0
max-length-break: 128
mini-batch-words: 1024
workspace: 128
max-length-factor: 2.0
skip-cost: true
cpu-threads: 0
quiet: false
quiet-translation: false
gemm-precision: int8shiftAlphaAll
alignment: soft
)"""
    }
}

sealed class TranslationResult {
    data class Success(val text: String) : TranslationResult()
    data class Error(val message: String) : TranslationResult()
}