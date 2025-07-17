package com.example.translator

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TranslatorApp(
    initialText: String,
    detectedLanguage: Language? = null,
    sharedImageUri: Uri? = null,
    translationCoordinator: TranslationCoordinator,
    onOcrProgress: ((Float) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Check if any languages are available
    var hasLanguages by remember { mutableStateOf<Boolean?>(null) } // null = checking, true/false = result

    // Check for available languages on startup
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val anyLanguageAvailable = Language.entries.any { language ->
                checkLanguagePairFiles(context, language, Language.ENGLISH) ||
                        checkLanguagePairFiles(context, Language.ENGLISH, language)
            }
            hasLanguages = anyLanguageAvailable
        }
    }

    // Watch for when all languages are deleted and navigate accordingly
    // Only auto-navigate if we're on main screen and have no languages
    LaunchedEffect(hasLanguages) {
        if (hasLanguages == false && navController.currentDestination?.route == "main") {
            // If user is on main page and no languages available, go to language manager
            navController.navigate("language_manager") {
                popUpTo("main") { inclusive = true }
            }
        }
    }

    // Move all persistent state to this level so it survives navigation
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    val (from, setFrom) = remember { mutableStateOf(detectedLanguage ?: Language.SPANISH) }
    val (to, setTo) = remember { mutableStateOf(Language.ENGLISH) }
    var displayImage by remember { mutableStateOf<Bitmap?>(null) }
    
    // Translation request handlers
    val scope = rememberCoroutineScope()
    
    val onTranslateRequest: (Language, Language, String) -> Unit = { fromLang, toLang, text ->
        scope.launch {
            val result = translationCoordinator.translateText(fromLang, toLang, text)
            result?.let { output = it }
        }
    }
    
    val onDetectLanguageRequest: (String) -> Unit = { text ->
        scope.launch {
            val detected = translationCoordinator.detectLanguage(text)
            detected?.let { 
                if (it != from) {
                    setFrom(it)
                    if (to == it) {
                        setTo(Language.ENGLISH)
                    }
                    // Auto-translate with new language
                    val result = translationCoordinator.translateText(it, to, text)
                    result?.let { output = it }
                }
            }
        }
    }
    
    val onTranslateImageRequest: (Uri) -> Unit = { uri ->
        scope.launch {
            val result = translationCoordinator.translateImage(from, to, uri) { originalBitmap ->
                displayImage = originalBitmap
            }
            result?.let { 
                displayImage = it.correctedBitmap
                input = it.extractedText
                output = it.translatedText
            }
        }
    }
    
    val onTranslateImageWithOverlayRequest: (Uri) -> Unit = { uri ->
        scope.launch {
            val result = translationCoordinator.translateImageWithOverlay(from, to, uri) { originalBitmap ->
                displayImage = originalBitmap
            }
            result?.let { 
                displayImage = it.correctedBitmap
                output = it.translatedText
            }
        }
    }

    // Determine start destination based on language availability
    val startDestination = when (hasLanguages) {
        null -> "loading" // Still checking
        true -> "main"    // Languages available, go to main
        false -> "language_manager" // No languages, go to manager
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("loading") {
            // Simple loading screen while checking languages
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        composable("main") {
            Greeting(
                onManageLanguages = { navController.navigate("language_manager") },
                input = input,
                onInputChange = { input = it },
                output = output,
                from = from,
                onFromChange = setFrom,
                to = to,
                onToChange = setTo,
                displayImage = displayImage,
                isTranslating = translationCoordinator.isTranslating,
                onTranslateRequest = onTranslateRequest,
                onDetectLanguageRequest = onDetectLanguageRequest,
                onTranslateImageRequest = onTranslateImageRequest,
                onTranslateImageWithOverlayRequest = onTranslateImageWithOverlayRequest,
                onOcrProgress = onOcrProgress,
                sharedImageUri = sharedImageUri,
            )
        }
        composable("language_manager") {
            LanguageManagerScreen(
                onLanguageDownloaded = {
                    if (hasLanguages == false) {
                        hasLanguages = true
                        MainScope().launch {
                            navController.navigate("main") {
                                popUpTo("language_manager") { inclusive = true }
                            }
                        }
                    }
                },
                onLanguageDeleted = {
                    // Update state when all languages are deleted
                    hasLanguages = false
                }
            )
        }
    }
}