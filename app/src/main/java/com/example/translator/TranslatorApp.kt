package com.example.translator


import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.googlecode.tesseract.android.TessBaseAPI

@Composable
fun TranslatorApp(configForLang: (Language, Language) -> String, initialText:String, detectedLanguage: Language? = null, ocrService: OCRService, onOcrProgress: ((Float) -> Unit) -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            Greeting(
                configForLang = configForLang,
                onManageLanguages = { navController.navigate("language_manager")                },
                initialText = initialText,
                detectedLanguage = detectedLanguage,
                ocrService = ocrService,
                onOcrProgress = onOcrProgress,
            )
        }
        composable("language_manager") {
            LanguageManagerScreen()
        }
    }
}