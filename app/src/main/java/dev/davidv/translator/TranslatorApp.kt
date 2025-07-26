package dev.davidv.translator

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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


    // Move all persistent state to this level so it survives navigation
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    val (from, setFrom) = remember { mutableStateOf(availableLanguages.firstOrNull()) }
    val (to, setTo) = remember { mutableStateOf(settings.defaultTargetLanguage) }
    var displayImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentDetectedLanguage by remember { mutableStateOf<Language?>(null) }
    var inputType by remember { mutableStateOf(InputType.TEXT) }
    var originalImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Translation request handlers
    val scope = rememberCoroutineScope()
    
    // Function to refresh available languages
    val refreshAvailableLanguages = {
        scope.launch {
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
                // Only consider it as having languages if there are non-English languages
                hasLanguages = available.any { it != Language.ENGLISH }
                if (hasLanguages as Boolean && from == null) {
                    setFrom(availableLanguages.filterNot { lang -> lang == settingsManager.settings.value.defaultTargetLanguage }.first())
                }
            }
        }
    }

    // Check for available languages on startup
    LaunchedEffect(Unit) {
        refreshAvailableLanguages()
    }
    
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
                translated = translationCoordinator.translateText(from!!, to, initialText)
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
                        val translated = translationCoordinator.translateText(from!!, to, message.text)
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
                translateWithLanguages(from!!, message.language)
            }
            
            is TranslatorMessage.SetImageUri -> {
                // Store the original image URI and set input type
                originalImageUri = message.uri
                inputType = InputType.IMAGE
                currentDetectedLanguage = null
                output = ""
                scope.launch {
                    val result = translationCoordinator.translateImageWithOverlay(from!!, to, message.uri) { originalBitmap ->
                        displayImage = originalBitmap
                    }
                    result?.let { 
                        displayImage = it.correctedBitmap
                        output = it.translatedText
                    }
                }
            }
            
            TranslatorMessage.SwapLanguages -> {
                val oldFrom = from!!
                val oldTo = to
                setFrom(oldTo)
                setTo(oldFrom)
                translateWithLanguages(oldTo, oldFrom)
            }
            
            TranslatorMessage.ClearImage -> {
                displayImage = null
                output = ""
                input = ""
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

    // Determine start destination based on language availability - only calculate once
    val startDestination = remember {
        when (hasLanguages) {
            null -> "loading" // Still checking
            true -> "main"    // Languages available, go to main
            false -> "no_languages" // No languages, go to no languages screen
        }
    }
    
    // Handle navigation when hasLanguages state changes (only from loading screen)
    LaunchedEffect(hasLanguages) {
        val currentRoute = navController.currentDestination?.route
        
        if (hasLanguages != null && currentRoute == "loading") {
            // Initial navigation from loading screen
            val destination = if (hasLanguages == true) "main" else "no_languages"
            navController.navigate(destination) {
                popUpTo("loading") { inclusive = true }
            }
        }
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

        composable("no_languages") {
            NoLanguagesScreen(
                onLanguageDownloaded = {
                    // Refresh available languages after download
                    refreshAvailableLanguages()
                },
                onLanguageDeleted = {
                    // Refresh available languages after deletion
                    refreshAvailableLanguages()
                },
                onDone = {
                    // Navigate to main screen when Done is pressed
                    MainScope().launch {
                        navController.navigate("main") {
                            popUpTo("no_languages") { inclusive = true }
                        }
                    }
                },
                hasLanguages = hasLanguages == true
            )
        }
        
        composable("main") {
            // Guard: redirect to no_languages if no languages available
            if (hasLanguages == false) {
                LaunchedEffect(Unit) {
                    navController.navigate("no_languages") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            } else {
                Greeting(
                    // Navigation
                    onSettings = { navController.navigate("settings") },
                    
                    // Current state (read-only)
                    input = input,
                    output = output,
                    from = from!!,
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
        }
        composable("language_manager") {
            LanguageManagerScreen(
                onLanguageDownloaded = {
                    // Refresh available languages after download
                    refreshAvailableLanguages()
                },
                onLanguageDeleted = {
                    // Refresh available languages after deletion
                    refreshAvailableLanguages()
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