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

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.davidv.translator.AppSettings
import dev.davidv.translator.DownloadState
import dev.davidv.translator.Language
import dev.davidv.translator.LaunchMode
import dev.davidv.translator.R
import dev.davidv.translator.TranslatedText
import dev.davidv.translator.TranslatorMessage
import dev.davidv.translator.ui.components.DetectedLanguageSection
import dev.davidv.translator.ui.components.InputSection
import dev.davidv.translator.ui.components.LanguageSelectionRow
import dev.davidv.translator.ui.components.TranslationField
import dev.davidv.translator.ui.components.ZoomableImageViewer
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
  // Navigation
  onSettings: () -> Unit,
  // Current state (read-only)
  input: String,
  output: TranslatedText?,
  from: Language,
  to: Language,
  detectedLanguage: Language?,
  displayImage: Bitmap?,
  isTranslating: StateFlow<Boolean>,
  isOcrInProgress: StateFlow<Boolean>,
  launchMode: LaunchMode,
  // Action requests
  onMessage: (TranslatorMessage) -> Unit,
  // System integration
  sharedImageUri: Uri? = null,
  availableLanguages: Map<String, Boolean>,
  downloadStates: Map<Language, DownloadState> = emptyMap(),
  settings: AppSettings,
) {
  var showFullScreenImage by remember { mutableStateOf(false) }
  var showImageSourceSheet by remember { mutableStateOf(false) }
  val translating by isTranslating.collectAsState()
  val extraTopPadding = if (launchMode == LaunchMode.Normal) 0.dp else 8.dp
  // Process shared image when component loads
  LaunchedEffect(sharedImageUri) {
    if (sharedImageUri != null) {
      Log.d("SharedImage", "Processing shared image: $sharedImageUri")
      onMessage(TranslatorMessage.SetImageUri(sharedImageUri))
    }
  }
  val context = LocalContext.current

  // Create temporary file for camera capture
  val cameraImageUri =
    remember {
      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
      val imageFile = File(context.cacheDir, "camera_image_$timeStamp.jpg")
      try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
      } catch (e: IllegalArgumentException) {
        Log.e("MainActivity", "Failed to find FAB image. Should only happen during development")
        // This can happen during previews when the FileProvider isn't available
        Uri.EMPTY
      }
    }
  // Camera launcher using MediaStore intent with EXTRA_OUTPUT
  val takePictureIntent =
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
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
    floatingActionButton = {
      when (launchMode) {
        LaunchMode.Normal -> {
          if (!settings.disableOcr) {
            FloatingActionButton(onClick = {
              showImageSourceSheet = true
            }) {
              Icon(
                painterResource(id = R.drawable.add_photo),
                contentDescription = "Translate image",
              )
            }
          }
        }

        LaunchMode.ReadonlyModal -> {
        }

        is LaunchMode.ReadWriteModal -> {
          if (output != null) {
            FloatingActionButton(
              onClick = {
                launchMode.reply(output.translated)
              },
              shape = FloatingActionButtonDefaults.largeShape,
            ) {
              Icon(
                painterResource(id = R.drawable.check),
                contentDescription = "Replace text",
                modifier = Modifier.size(30.dp),
              )
            }
          }
        }
      }
    },
  ) { paddingValues ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .navigationBarsPadding()
          .imePadding()
          .padding(top = paddingValues.calculateTopPadding() + extraTopPadding, bottom = 0.dp),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
      ) {
        LanguageSelectionRow(
          from = from,
          to = to,
          availableLanguages = availableLanguages,
          translating = translating,
          onMessage = onMessage,
          onSettings = if (launchMode == LaunchMode.Normal) onSettings else null,
        )

        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(0.4f),
        ) {
          InputSection(
            displayImage = displayImage,
            input = input,
            isOcrInProgress = isOcrInProgress,
            isTranslating = isTranslating,
            onMessage = onMessage,
            onShowFullScreenImage = { showFullScreenImage = true },
          )
        }

        DetectedLanguageSection(
          detectedLanguage = detectedLanguage,
          from = from,
          availableLanguages = availableLanguages,
          onMessage = onMessage,
          downloadStates = downloadStates,
        )
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          contentAlignment = Alignment.Center,
        ) {
          HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.5f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
          )
        }
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .weight(0.5f),
        ) {
          if (output != null) {
            TranslationField(
              text = output,
            )
          }
        }
      }
    }

    // Image source selection bottom sheet
    if (showImageSourceSheet) {
      ImageSourceBottomSheet(
        onDismiss = { showImageSourceSheet = false },
        onCameraClick = {
          showImageSourceSheet = false
          val cameraIntent =
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
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
        },
      )
    }

    // Full screen image viewer
    if (showFullScreenImage && displayImage != null) {
      ZoomableImageViewer(
        bitmap = displayImage,
        onDismiss = { showFullScreenImage = false },
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
  onGalleryClick: () -> Unit,
) {
  val bottomSheetState = rememberModalBottomSheetState()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = bottomSheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        // Camera (always present)
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable { onCameraClick() },
        ) {
          Icon(
            painter = painterResource(id = R.drawable.camera),
            contentDescription = "Camera",
            modifier =
              Modifier
                .size(48.dp)
                .padding(bottom = 8.dp),
            tint = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Camera",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
        }

        // Conditional: Photos (Android 13+) or Gallery (older versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          // Modern Photos picker for Android 13+
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onMediaPickerClick() },
          ) {
            Icon(
              painter = painterResource(id = R.drawable.gallery),
              contentDescription = "Photos",
              modifier =
                Modifier
                  .size(48.dp)
                  .padding(bottom = 8.dp),
              tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Photos",
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
            )
          }
        } else {
          // Traditional Gallery for older Android versions
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onGalleryClick() },
          ) {
            Icon(
              painter = painterResource(id = R.drawable.gallery),
              contentDescription = "Gallery",
              modifier =
                Modifier
                  .size(48.dp)
                  .padding(bottom = 8.dp),
              tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Gallery",
              style = MaterialTheme.typography.bodyMedium,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
  }
}

@Composable
fun WideDialogTheme(content: @Composable () -> Unit) {
  TranslatorTheme {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(Color.Transparent),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier =
          Modifier
            .fillMaxWidth(0.9f)
            .height((LocalConfiguration.current.screenHeightDp * 0.5f).dp)
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
      ) {
        content()
      }
    }
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PopupMode() {
  WideDialogTheme {
    MainScreen(
      onSettings = { },
      input = "Example input",
      output = TranslatedText("Example output", null),
      from = Language.AZERBAIJANI,
      to = Language.SPANISH,
      detectedLanguage = Language.FRENCH,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      sharedImageUri = null,
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.ReadWriteModal {},
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MainScreenPreview() {
  TranslatorTheme {
    MainScreen(
      onSettings = { },
      input = "Example input",
      output = TranslatedText("Example output", null),
      from = Language.ENGLISH,
      to = Language.SPANISH,
      detectedLanguage = Language.FRENCH,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      sharedImageUri = null,
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.Normal,
    )
  }
}

@Preview(
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PreviewVeryLongText() {
  TranslatorTheme {
    MainScreen(
      onSettings = { },
      input = "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
      output =
        TranslatedText(
          "very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text. very long text.",
          null,
        ),
      from = Language.ENGLISH,
      to = Language.ENGLISH,
      detectedLanguage = null,
      displayImage = null,
      isTranslating = MutableStateFlow(false).asStateFlow(),
      isOcrInProgress = MutableStateFlow(false).asStateFlow(),
      onMessage = {},
      sharedImageUri = null,
      availableLanguages =
        mapOf(
          Language.ENGLISH.code to true,
          Language.SPANISH.code to true,
          Language.FRENCH.code to true,
        ),
      downloadStates = emptyMap(),
      settings = AppSettings(),
      launchMode = LaunchMode.Normal,
    )
  }
}
