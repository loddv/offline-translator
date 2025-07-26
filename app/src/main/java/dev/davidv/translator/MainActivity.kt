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
import androidx.core.content.FileProvider
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    val translating by isTranslating.collectAsState()

    // Process shared image when component loads
    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            Log.d("SharedImage", "Processing shared image: $sharedImageUri")
            onMessage(TranslatorMessage.SetImageUri(sharedImageUri))
        }
    }
    val context = LocalContext.current

    // Create temporary file for camera capture
    val cameraImageUri = remember {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(context.cacheDir, "camera_image_$timeStamp.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }
    // Camera launcher using MediaStore intent with EXTRA_OUTPUT
    val takePictureIntent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            // When using EXTRA_OUTPUT, the full image is saved to the URI we provided
            Log.d("Camera", "Photo captured: $cameraImageUri")
            onMessage(TranslatorMessage.SetImageUri(cameraImageUri))
        } else {
            Log.d("Camera", "Photo capture cancelled or failed")
        }
    }

    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                onMessage(TranslatorMessage.SetImageUri(uri))
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }
    
    val pickFromGallery =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val imageUri = result.data?.data
                if (imageUri != null) {
                    Log.d("Gallery", "Selected URI: $imageUri")
                    onMessage(TranslatorMessage.SetImageUri(imageUri))
                } else {
                    Log.d("Gallery", "No image selected")
                }
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showImageSourceSheet = true
            }) {
                Icon(
                    painterResource(id = R.drawable.add_photo),
                    contentDescription = "Translate image",
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
                .padding(top = paddingValues.calculateTopPadding(), bottom = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(rememberScrollState())
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFullScreenImage = true }
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
                            .height(200.dp)
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
                    .height(300.dp)
            )
            
            // Add extra padding at bottom to ensure content can be scrolled fully into view
            Spacer(modifier = Modifier.height(100.dp))
            }
        }
        
        // Image source selection bottom sheet
        if (showImageSourceSheet) {
            ImageSourceBottomSheet(
                onDismiss = { showImageSourceSheet = false },
                onCameraClick = {
                    showImageSourceSheet = false
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                    }
                    takePictureIntent.launch(cameraIntent)
                },
                onMediaPickerClick = {
                    showImageSourceSheet = false
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onGalleryClick = {
                    showImageSourceSheet = false
                    val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickFromGallery.launch(galleryIntent)
                }
            )
        }

        // Full screen image viewer
        if (showFullScreenImage && displayImage != null) {
            ZoomableImageViewer(
                bitmap = displayImage,
                onDismiss = { showFullScreenImage = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSourceBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onMediaPickerClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onCameraClick() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add, // TODO
                        contentDescription = "Camera",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Camera",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onMediaPickerClick() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add, // TODO
                        contentDescription = "Media Picker",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Media Picker",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onGalleryClick() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add, // TODO
                        contentDescription = "Gallery",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(bottom = 8.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
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
