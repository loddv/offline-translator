package com.example.translator

data class AppSettings(
    val defaultTargetLanguage: Language = Language.ENGLISH,
    val translationModelsBaseUrl: String = Constants.DEFAULT_TRANSLATION_MODELS_BASE_URL,
    val tesseractModelsBaseUrl: String = Constants.DEFAULT_TESSERACT_MODELS_BASE_URL,
    val backgroundMode: BackgroundMode = BackgroundMode.AUTO_DETECT,
    val minConfidence: Int = 75
)