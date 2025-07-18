package com.example.translator

import com.example.bergamot.LangDetect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageDetector {
    
    private val langDetect = LangDetect()


    suspend fun detectLanguage(text: String): Language? = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext null
        }

        val detected = langDetect.detectLanguage(text)
        if (detected.isReliable) {
            Language.entries.firstOrNull { it.code == detected.language }
        } else {
            null
        }
    }
}