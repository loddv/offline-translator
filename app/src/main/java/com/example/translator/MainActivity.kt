package com.example.translator

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.translator.ui.theme.TranslatorTheme
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
fun TranslationResult(
    context: Context, text: String, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp / 2),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                SelectionContainer(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp - 66).dp)
                    )
                }

                IconButton(
                    onClick = {
                        val clipboard: ClipboardManager? =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
                        val clip = ClipData.newPlainText("Translated text", text)
                        clipboard?.setPrimaryClip(clip)
                    }, modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.copy),
                        contentDescription = "Copy translation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(
    // Navigation
    onManageLanguages: () -> Unit,
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

    // Check if any languages are available
    val hasAnyLanguages = availableLanguages.values.any { it }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (hasAnyLanguages) {
                FloatingActionButton(onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(
                        painterResource(id = R.drawable.add_photo),
                        contentDescription = "Translate image",
                    )
                }
            }
        }
    ) { paddingValues ->

        if (!hasAnyLanguages) {
            // Show message when no languages are available
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Language Packs Installed",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Download language packs to start translating",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onManageLanguages
                ) {
                    Text("Download Languages")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clean language selectors
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
                    TranslationField(
                        text = input,
                        onTextChange = { newInput ->
                            onMessage(TranslatorMessage.TextInput(newInput))
                        },
                        placeholder = "Enter text",
                        isInput = true,
                        modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.fillMaxWidth(0.33f),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                TranslationField(
                    text = output,
                    isInput = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
            onManageLanguages = { },
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
fun PreviewNoLang() {
    TranslatorTheme {
        Greeting(
            onManageLanguages = { },
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
            availableLanguages = mapOf(),
        )
    }
}