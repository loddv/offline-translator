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

package dev.davidv.translator.ui.screens

import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.davidv.translator.DownloadService
import dev.davidv.translator.Greeting
import dev.davidv.translator.InputType
import dev.davidv.translator.Language
import dev.davidv.translator.LanguageManagerScreen
import dev.davidv.translator.LanguageStateManager
import dev.davidv.translator.SettingsManager
import dev.davidv.translator.TranslationCoordinator
import dev.davidv.translator.TranslatorMessage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

@Composable
fun TranslatorApp(
    initialText: String,
    sharedImageUri: Uri? = null,
    translationCoordinator: TranslationCoordinator,
    settingsManager: SettingsManager,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Settings management
    val settings by settingsManager.settings.collectAsState()

    // Centralized language state management
    val languageStateManager = remember { LanguageStateManager(context, scope) }
    val languageState by languageStateManager.languageState.collectAsState()

    // Download service management
    var downloadService by remember { mutableStateOf<DownloadService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as DownloadService.DownloadBinder
                downloadService = binder.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                downloadService = null
            }
        }
    }

    // Bind to download service
    DisposableEffect(context) {
        val intent = android.content.Intent(context, DownloadService::class.java)
        context.bindService(intent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    // Get download states from service
    val downloadStates by downloadService?.downloadStates?.collectAsState()
        ?: remember { mutableStateOf(emptyMap()) }

    // Refresh language availability when download states change
    LaunchedEffect(downloadStates) {
        // Refresh whenever download states change (covers downloads, deletions, etc.)
        languageStateManager.refreshLanguageAvailability()
    }


    // Move all persistent state to this level so it survives navigation
    var input by remember { mutableStateOf(initialText) }
    var output by remember { mutableStateOf("") }
    val (from, setFrom) = remember { mutableStateOf<Language?>(null) }
    val (to, setTo) = remember { mutableStateOf(settings.defaultTargetLanguage) }
    var displayImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentDetectedLanguage by remember { mutableStateOf<Language?>(null) }
    var inputType by remember { mutableStateOf(InputType.TEXT) }
    var originalImageUri by remember { mutableStateOf<Uri?>(null) }

    // Initialize from language when languages become available
    LaunchedEffect(languageState.availableLanguages, settings.defaultTargetLanguage) {
        if (from == null && languageState.availableLanguages.isNotEmpty()) {
            val firstAvailable =
                languageStateManager.getFirstAvailableFromLanguage(excluding = settings.defaultTargetLanguage)
            if (firstAvailable != null) {
                setFrom(firstAvailable)
                translationCoordinator.translationService.preloadModel(firstAvailable, to)
            }
        }
    }

    // Auto-translate initial text if provided
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            currentDetectedLanguage = translationCoordinator.detectLanguage(initialText)
            println("launched effect detected $currentDetectedLanguage")
            val translated: String?
            if (currentDetectedLanguage != null) {
                setFrom(currentDetectedLanguage!!)
                translated =
                    translationCoordinator.translateText(currentDetectedLanguage!!, to, initialText)
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
                        val result = translationCoordinator.translateImageWithOverlay(fromLang,
                            toLang,
                            uri,
                            { originalBitmap ->
                                displayImage = originalBitmap
                            }) { imageTextDetected ->
                            scope.launch {
                                currentDetectedLanguage =
                                    translationCoordinator.detectLanguage(imageTextDetected.extractedText)
                            }
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
                        currentDetectedLanguage =
                            translationCoordinator.detectLanguage(message.text)
                    }
                    // Auto-translate with current languages
                    scope.launch {
                        val translated =
                            translationCoordinator.translateText(from!!, to, message.text)
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
                    val result = translationCoordinator.translateImageWithOverlay(from!!,
                        to,
                        message.uri,
                        { originalBitmap ->
                            displayImage = originalBitmap
                        }) { imageTextDetected ->
                        scope.launch {
                            currentDetectedLanguage =
                                translationCoordinator.detectLanguage(imageTextDetected.extractedText)
                        }
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
                scope.launch {
                    translationCoordinator.translationService.preloadModel(oldTo, oldFrom)
                    translateWithLanguages(oldTo, oldFrom)
                }
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

            is TranslatorMessage.ImageTextDetected -> {
                scope.launch {
                    currentDetectedLanguage =
                        translationCoordinator.detectLanguage(message.extractedText)
                }
            }
        }
    }

    // Determine start destination based on initial language availability (fixed at first composition)
    val startDestination = remember {
        if (languageState.isChecking) {
            "loading"
        } else if (languageState.hasLanguages) {
            "main"
        } else {
            "no_languages"
        }
    }

    // Handle initial navigation from loading screen only
    LaunchedEffect(languageState.isChecking) {
        val currentRoute = navController.currentDestination?.route

        if (!languageState.isChecking && currentRoute == "loading") {
            // Initial navigation from loading screen only
            val destination = if (languageState.hasLanguages) "main" else "no_languages"
            navController.navigate(destination) {
                popUpTo("loading") { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable("loading") {
            // Simple loading screen while checking languages
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        composable("no_languages") {
            NoLanguagesScreen(onLanguageDownloaded = {
                // Refresh available languages after download
                languageStateManager.refreshLanguageAvailability()
            }, onLanguageDeleted = {
                // Refresh available languages after deletion
                languageStateManager.refreshLanguageAvailability()
            }, onDone = {
                // Only navigate if languages are available
                if (languageState.hasLanguages) {
                    MainScope().launch {
                        navController.navigate("main") {
                            popUpTo("no_languages") { inclusive = true }
                        }
                    }
                }
            }, onSettings = {
                navController.navigate("settings")
            }, hasLanguages = languageState.hasLanguages
            )
        }

        composable("main") {
            // Guard: redirect to no_languages if no languages available (only on initial load)
            if (!languageState.hasLanguages && !languageState.isChecking) {
                LaunchedEffect(Unit) {
                    navController.navigate("no_languages") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            } else if (from != null) {
                Greeting(
                    // Navigation
                    onSettings = { navController.navigate("settings") },
                    onDownloadLanguage = { language ->
                        navController.navigate("language_manager")
                    },

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
                    availableLanguages = languageState.availableLanguageMap,
                    downloadService = downloadService,
                    downloadStates = downloadStates,
                )
            }
        }
        composable("language_manager") {
            LanguageManagerScreen(onLanguageDownloaded = {
                // Refresh available languages after download
                languageStateManager.refreshLanguageAvailability()
            }, onLanguageDeleted = {
                // Refresh available languages after deletion
                languageStateManager.refreshLanguageAvailability()
            })
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                availableLanguages = if (languageState.availableLanguages.contains(Language.ENGLISH)) {
                    languageState.availableLanguages
                } else {
                    languageState.availableLanguages + Language.ENGLISH
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