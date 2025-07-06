package com.example.translator


import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.googlecode.tesseract.android.TessBaseAPI

@Composable
fun TranslatorApp(configForLang: (Language, Language) -> String, initialText:String, detectedLanguage: Language? = null, ocrService: OCRService, onOcrProgress: ((Float) -> Unit) -> Unit) {
    val navController = rememberNavController()
    
    // Move all persistent state to this level so it survives navigation
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    val (from, setFrom) = remember { mutableStateOf(detectedLanguage ?: Language.SPANISH) }
    val (to, setTo) = remember { mutableStateOf(Language.ENGLISH) }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            Greeting(
                configForLang = configForLang,
                onManageLanguages = { navController.navigate("language_manager") },
                input = input,
                onInputChange = { input = it },
                output = output,
                onOutputChange = { output = it },
                from = from,
                onFromChange = setFrom,
                to = to,
                onToChange = setTo,
                ocrService = ocrService,
                onOcrProgress = onOcrProgress,
            )
        }
        composable("language_manager") {
            LanguageManagerScreen()
        }
    }
}