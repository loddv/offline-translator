package com.example.translator

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.runtime.collectAsState

@Composable
fun TranslatorApp(
    initialText: String,
    sharedImageUri: Uri? = null,
    translationCoordinator: TranslationCoordinator,
    settingsManager: SettingsManager,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Settings management
    val settings by settingsManager.settings.collectAsState()

    // Check if any languages are available
    var hasLanguages by remember { mutableStateOf<Boolean?>(null) } // null = checking, true/false = result
    var availableLanguages by remember { mutableStateOf<List<Language>>(emptyList()) }
    
    // Track available language pairs (detailed map for UI components)
    val availableLanguageMap = remember { mutableStateMapOf<String, Boolean>() }

    // Check for available languages on startup
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Set English as always available
            availableLanguageMap[Language.ENGLISH.code] = true
            
            // Check each language pair
            Language.entries.forEach { fromLang ->
                val toLang = Language.ENGLISH
                if (fromLang != toLang) {
                    val isAvailable = checkLanguagePairFiles(context, fromLang, toLang)
                    availableLanguageMap[fromLang.code] = isAvailable
                }
            }
            
            // Create list of available languages
            val available = Language.entries.filter { language ->
                availableLanguageMap[language.code] == true
            }
            availableLanguages = available
            hasLanguages = available.isNotEmpty()
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
    val (from, setFrom) = remember { mutableStateOf(Language.SPANISH) }
    val (to, setTo) = remember { mutableStateOf(settings.defaultTargetLanguage) }
    var displayImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentDetectedLanguage by remember { mutableStateOf<Language?>(null) }
    var inputType by remember { mutableStateOf(InputType.TEXT) }
    var originalImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Translation request handlers
    val scope = rememberCoroutineScope()
    
    // Auto-translate initial text if provided
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            currentDetectedLanguage = translationCoordinator.detectLanguage(initialText)
            println("launched effect detected $currentDetectedLanguage")
            val translated: String?
            if (currentDetectedLanguage != null) {
                setFrom(currentDetectedLanguage!!)
                // TODO: SetTo to the first non-from language, if any
                translated = translationCoordinator.translateText(currentDetectedLanguage!!, to, initialText)
            } else {
                translated = translationCoordinator.translateText(from, to, initialText)
            }
            translated?.let { output = it }
        }
    }
    
    // Reusable translation closure based on input type
    val translateWithLanguages: (Language, Language) -> Unit = { fromLang, toLang ->
        scope.launch {
            when (inputType) {
                InputType.TEXT -> {
                    val result = translationCoordinator.translateText(fromLang, toLang, input)
                    result?.let { output = it }
                }
                InputType.IMAGE -> {
                    originalImageUri?.let { uri ->
                        val result = translationCoordinator.translateImageWithOverlay(fromLang, toLang, uri) { originalBitmap ->
                            displayImage = originalBitmap
                        }
                        result?.let { 
                            displayImage = it.correctedBitmap
                            output = it.translatedText
                        }
                    }
                }
            }
        }
    }
    
    // Centralized message handler
    val handleMessage: (TranslatorMessage) -> Unit = { message ->
        when (message) {
            is TranslatorMessage.TextInput -> {
                input = message.text
                // Detect language in background for auto-suggest button
                if (message.text.isNotBlank()) {
                    scope.launch {
                        currentDetectedLanguage = translationCoordinator.detectLanguage(message.text)
                    }
                    // Auto-translate with current languages
                    scope.launch {
                        val translated = translationCoordinator.translateText(from, to, message.text)
                        translated?.let { output = it }
                    }
                } else {
                    currentDetectedLanguage = null
                    output = ""
                }
            }
            
            is TranslatorMessage.FromLang -> {
                setFrom(message.language)
                translateWithLanguages(message.language, to)
            }
            
            is TranslatorMessage.ToLang -> {
                setTo(message.language)
                translateWithLanguages(from, message.language)
            }
            
            is TranslatorMessage.SetImageUri -> {
                // Store the original image URI and set input type
                originalImageUri = message.uri
                inputType = InputType.IMAGE
                currentDetectedLanguage = null
                scope.launch {
                    val result = translationCoordinator.translateImageWithOverlay(from, to, message.uri) { originalBitmap ->
                        displayImage = originalBitmap
                    }
                    result?.let { 
                        displayImage = it.correctedBitmap
                        output = it.translatedText
                    }
                }
            }
            
            TranslatorMessage.SwapLanguages -> {
                val oldFrom = from
                val oldTo = to
                setFrom(oldTo)
                setTo(oldFrom)
                translateWithLanguages(oldTo, oldFrom)
            }
            
            TranslatorMessage.ClearImage -> {
                displayImage = null
                output = ""
                inputType = InputType.TEXT
                originalImageUri = null
                currentDetectedLanguage = null
            }
            
            is TranslatorMessage.InitializeLanguages -> {
                setFrom(message.from)
                setTo(message.to)
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
                // Navigation
                onManageLanguages = { navController.navigate("language_manager") },
                onSettings = { navController.navigate("settings") },
                
                // Current state (read-only)
                input = input,
                output = output,
                from = from,
                to = to,
                detectedLanguage = currentDetectedLanguage,
                displayImage = displayImage,
                isTranslating = translationCoordinator.isTranslating,
                isOcrInProgress = translationCoordinator.isOcrInProgress,
                
                // Action requests
                onMessage = handleMessage,
                
                // System integration
                sharedImageUri = sharedImageUri,
                availableLanguages = availableLanguageMap,
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
        composable("settings") {
            SettingsScreen(
                settings = settings,
                availableLanguages = if (availableLanguages.contains(Language.ENGLISH)) {
                    availableLanguages
                } else {
                    availableLanguages + Language.ENGLISH
                },
                onSettingsChange = { newSettings ->
                    settingsManager.updateSettings(newSettings)
                    // Update current target language if it changed
                    if (newSettings.defaultTargetLanguage != settings.defaultTargetLanguage) {
                        setTo(newSettings.defaultTargetLanguage)
                    }
                },
                onManageLanguages = {
                    navController.navigate("language_manager")
                },
            )
        }
    }
}