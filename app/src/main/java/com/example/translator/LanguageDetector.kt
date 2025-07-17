package com.example.translator

import com.example.bergamot.DetectionResult
import com.example.bergamot.LangDetect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageDetector {
    
    private val langDetect = LangDetect()
    
    suspend fun detectLanguage(text: String): DetectionResult? = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext null
        }
        
        val detected = langDetect.detectLanguage(text)
        if (detected.isReliable) {
            detected
        } else {
            null
        }
    }
    
    suspend fun detectLanguageEnum(text: String): Language? = withContext(Dispatchers.IO) {
        val detected = detectLanguage(text)
        detected?.let { result ->
            Language.entries.firstOrNull { it.code == result.language }
        }
    }
}