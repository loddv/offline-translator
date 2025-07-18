package com.example.translator

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    private fun loadSettings(): AppSettings {
        val defaultTargetLanguageCode = prefs.getString("default_target_language", Language.ENGLISH.code)
        val defaultTargetLanguage = Language.entries.find { it.code == defaultTargetLanguageCode } ?: Language.ENGLISH
        
        val translationModelsBaseUrl = prefs.getString("translation_models_base_url", 
            Constants.DEFAULT_TRANSLATION_MODELS_BASE_URL)
            ?: Constants.DEFAULT_TRANSLATION_MODELS_BASE_URL
        
        val tesseractModelsBaseUrl = prefs.getString("tesseract_models_base_url", 
            Constants.DEFAULT_TESSERACT_MODELS_BASE_URL)
            ?: Constants.DEFAULT_TESSERACT_MODELS_BASE_URL
        
        val backgroundModeName = prefs.getString("background_mode", BackgroundMode.AUTO_DETECT.name)
        val backgroundMode = try {
            BackgroundMode.valueOf(backgroundModeName ?: BackgroundMode.AUTO_DETECT.name)
        } catch (e: IllegalArgumentException) {
            BackgroundMode.AUTO_DETECT
        }
        
        val minConfidence = prefs.getInt("min_confidence", 75)
        
        return AppSettings(
            defaultTargetLanguage = defaultTargetLanguage,
            translationModelsBaseUrl = translationModelsBaseUrl,
            tesseractModelsBaseUrl = tesseractModelsBaseUrl,
            backgroundMode = backgroundMode,
            minConfidence = minConfidence
        )
    }
    
    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("default_target_language", newSettings.defaultTargetLanguage.code)
            putString("translation_models_base_url", newSettings.translationModelsBaseUrl)
            putString("tesseract_models_base_url", newSettings.tesseractModelsBaseUrl)
            putString("background_mode", newSettings.backgroundMode.name)
            putInt("min_confidence", newSettings.minConfidence)
            apply()
        }
        _settings.value = newSettings
    }
}