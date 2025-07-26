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

package dev.davidv.translator

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class MainActivity : ComponentActivity() {
    private var textToTranslate: String = ""
    private var sharedImageUri: Uri? = null
    private lateinit var ocrService: OCRService
    private lateinit var translationCoordinator: TranslationCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        ocrService = OCRService(this)
        val imageProcessor = ImageProcessor(this, ocrService)
        val translationService = TranslationService(this)
        val languageDetector = LanguageDetector()
        val settingsManager = SettingsManager(this)
        translationCoordinator = TranslationCoordinator(this, translationService, languageDetector, imageProcessor, settingsManager)

        setContent {
            TranslatorTheme {
                MaterialTheme {
                    TranslatorApp(
                        initialText = textToTranslate,
                        sharedImageUri = sharedImageUri,
                        translationCoordinator = translationCoordinator,
                        settingsManager = settingsManager
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrService.cleanup()
        TranslationService.cleanup()
        println("cleaning up main activity")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = if (intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
                    intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                textToTranslate = text ?: ""
            }

            Intent.ACTION_SEND -> {
                // Check if it's text or image
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

                if (text != null) {
                    textToTranslate = text
                } else if (imageUri != null) {
                    sharedImageUri = imageUri
                    textToTranslate = "" // Clear any existing text
                }
            }
        }
    }
}

@Composable
fun Greeting(
    // Navigation
    onSettings: () -> Unit,
    
    // Current state (read-only)
    input: String,
    output: String,
    from: Language,
    to: Language,
    detectedLanguage: Language?,
    displayImage: Bitmap?,
    isTranslating: StateFlow<Boolean>,
    isOcrInProgress: StateFlow<Boolean>,
    
    // Action requests
    onMessage: (TranslatorMessage) -> Unit,
    
    // System integration
    sharedImageUri: Uri? = null,
    availableLanguages: Map<String, Boolean>,
) {

    val translating by isTranslating.collectAsState()

    // Process shared image when component loads
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            Log.d("SharedImage", "Processing shared image: $sharedImageUri")
            onMessage(TranslatorMessage.SetImageUri(sharedImageUri))
        }
    }

    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                onMessage(TranslatorMessage.SetImageUri(uri))
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(
                    painterResource(id = R.drawable.add_photo),
                    contentDescription = "Translate image",
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp)
                .padding(horizontal = 8.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fromLanguages = Language.entries.filter { x -> x != to && x != from && availableLanguages[x.code] == true }
                    val toLanguages = Language.entries.filter { x -> x != from && x != to && availableLanguages[x.code] == true }
                    
                    LanguageSelector(
                        selectedLanguage = from,
                        availableLanguages = fromLanguages,
                        onLanguageSelected = { language ->
                            onMessage(TranslatorMessage.FromLang(language))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = {
                        if (!translating) {
                            onMessage(TranslatorMessage.SwapLanguages)
                        }
                    }) {
                        Icon(
                            painterResource(id = R.drawable.compare),
                            contentDescription = "Reverse translation direction"
                        )
                    }
                    
                    LanguageSelector(
                        selectedLanguage = to,
                        availableLanguages = toLanguages,
                        onLanguageSelected = { language ->
                            if (!translating) {
                                onMessage(TranslatorMessage.ToLang(language))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = onSettings) {
                        Icon(
                            painterResource(id = R.drawable.settings),
                            contentDescription = "Settings"
                        )
                    }

                }
                Spacer(modifier = Modifier.height(8.dp))


                if (displayImage != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Progress bar for OCR and translation work
                            val isOcrInProgressState by isOcrInProgress.collectAsState()
                            val isTranslatingState by isTranslating.collectAsState()
                            val isProcessing = isOcrInProgressState || isTranslatingState
                            
                            if (isProcessing) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            
                            Image(
                                bitmap = displayImage.asImageBitmap(),
                                contentDescription = "Image to translate",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Close button in top-right corner
                        IconButton(
                            onClick = { onMessage(TranslatorMessage.ClearImage) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                painterResource(id = R.drawable.cancel),
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    StyledTextField(
                        text = input,
                        onValueChange = { newInput ->
                            onMessage(TranslatorMessage.TextInput(newInput))
                        },
                        placeholder = "Enter text",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // Detected language toast
                if (detectedLanguage != null && detectedLanguage != from) {
                    DetectedLanguageToast(
                        detectedLanguage = detectedLanguage,
                        onSwitchClick = {
                            onMessage(TranslatorMessage.FromLang(detectedLanguage))
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(0.5f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

            TranslationField(
                text = output,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}


@Preview(
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun GreetingPreview() {
    TranslatorTheme {
        Greeting(
            onSettings = {  },
            input = "Example input",
            output = "Example output",
            from = Language.ENGLISH,
            to = Language.SPANISH,
            detectedLanguage = Language.FRENCH,
            displayImage = null,
            isTranslating = MutableStateFlow(false).asStateFlow(),
            isOcrInProgress = MutableStateFlow(false).asStateFlow(),
            onMessage = {},
            sharedImageUri = null,
            availableLanguages = mapOf(
                Language.ENGLISH.code to true,
                Language.SPANISH.code to true,
                Language.FRENCH.code to true
            ),
        )
    }
}


@Preview(
    showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewVeryLongText() {
    TranslatorTheme {
        Greeting(
            onSettings = {  },
            input = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
            output = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
            from = Language.ENGLISH,
            to = Language.ENGLISH,
            detectedLanguage = null,
            displayImage = null,
            isTranslating = MutableStateFlow(false).asStateFlow(),
            isOcrInProgress = MutableStateFlow(false).asStateFlow(),
            onMessage = {},
            sharedImageUri = null,
            availableLanguages = mapOf(
                Language.ENGLISH.code to true,
                Language.SPANISH.code to true,
                Language.FRENCH.code to true
            ),
        )
    }
}
