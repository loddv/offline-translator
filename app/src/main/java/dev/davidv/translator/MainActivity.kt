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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.davidv.translator.browser.BrowserActivity
import dev.davidv.translator.ui.TranslatorViewModel
import dev.davidv.translator.ui.TranslatorViewModelFactory
import dev.davidv.translator.ui.screens.StartScreen
import dev.davidv.translator.ui.screens.TranslatorApp
import dev.davidv.translator.ui.theme.TranslatorTheme

class MainActivity : ComponentActivity() {
  companion object {
    const val EXTRA_OPEN_LANGUAGE_MANAGER = "dev.davidv.translator.OPEN_LANGUAGE_MANAGER"

    // Sent by the launcher's "Camera" shortcut (res/xml/shortcuts.xml).
    const val ACTION_OPEN_LIVE_CAMERA = "dev.davidv.translator.OPEN_LIVE_CAMERA"
  }

  // Identity in production; only the UI-export test flips UiExportRecorder.active to record which
  // R.string each rendered label resolved from.
  override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(UiExportRecorder.wrap(newBase))
  }

  private var textToTranslate: String = ""
  private var launchMode: LaunchMode = LaunchMode.Normal
  private var sharedImageUri: Uri? = null
  private var sharedDocumentUri: Uri? = null
  private var openLanguageManager: Boolean = false
  private lateinit var viewModel: TranslatorViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)
    if (isFinishing) return

    val app = application as TranslatorApplication

    viewModel =
      ViewModelProvider(
        this,
        TranslatorViewModelFactory(
          appContext = applicationContext,
          translationCoordinator = app.translationCoordinator,
          settingsManager = app.settingsManager,
          filePathManager = app.filePathManager,
          catalogRepository = app.catalogRepository,
          languageMetadataManager = app.languageMetadataManager,
          initialText = textToTranslate,
          initialLaunchMode = launchMode,
        ),
      )[TranslatorViewModel::class.java]

    // Deliver any shared input now that the ViewModel exists.
    sharedImageUri?.let { viewModel.setSharedImageUri(it) }
    sharedDocumentUri?.let { viewModel.documents.importShared(it) }
    if (openLanguageManager) {
      viewModel.requestLanguageManager()
      openLanguageManager = false
    }
    // Launchers start static shortcuts with NEW_TASK | CLEAR_TASK, so the shortcut always arrives
    // here and never through onNewIntent.
    val startScreen = if (intent?.action == ACTION_OPEN_LIVE_CAMERA) StartScreen.LiveCamera else StartScreen.Main

    setContent {
      TranslatorTheme {
        MigrationGate(
          catalogRepository = app.catalogRepository,
        ) {
          TranslatorApp(
            viewModel = viewModel,
            startScreen = startScreen,
            onClose = { finish() },
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i("MainActivity", "cleaning up")
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
    // Warm start (activity already created): deliver straight to the ViewModel,
    // since onCreate's one-shot delivery has already run.
    if (::viewModel.isInitialized) {
      sharedImageUri?.let {
        viewModel.setSharedImageUri(it)
        sharedImageUri = null
      }
      sharedDocumentUri?.let {
        viewModel.documents.importShared(it)
        sharedDocumentUri = null
      }
      if (openLanguageManager) {
        viewModel.requestLanguageManager()
        openLanguageManager = false
      }
    }
  }

  private fun handleIntent(intent: Intent?) {
    Log.d("MainActivity", "Got intent $intent")
    openLanguageManager = intent?.getBooleanExtra(EXTRA_OPEN_LANGUAGE_MANAGER, false) == true
    when (intent?.action) {
      Intent.ACTION_SEND -> {
        when (val payload = parseSendIntent(intent)) {
          is SharedText.Plain -> textToTranslate = payload.text
          is SharedText.WebUrl -> openUrl(payload.url)
          SharedText.UnsupportedDocumentUrl ->
            Toast.makeText(this, getString(R.string.doc_url_unsupported), Toast.LENGTH_LONG).show()
          is SharedPayload.Stream -> {
            // EXTRA_STREAM carries both shared images (→ crop) and shared documents
            // (→ the document drawer); route by MIME so a pdf/odt/txt/epub doesn't
            // get sent to the image cropper.
            if (isImageUri(this, payload.uri)) {
              sharedImageUri = payload.uri
            } else {
              sharedDocumentUri = payload.uri
            }
            textToTranslate = ""
          }
          SharedPayload.Empty -> Log.w("MainActivity", "ACTION_SEND carried neither text nor a stream")
        }
      }
      Intent.ACTION_VIEW -> {
        val url = intent.data?.toString()
        if (!url.isNullOrBlank() && isWebUrl(url)) {
          if (isSupportedDocumentUrl(url)) {
            Toast.makeText(this, getString(R.string.doc_url_unsupported), Toast.LENGTH_LONG).show()
          } else {
            openUrl(url)
            return
          }
        }
      }
    }
  }

  private fun openUrl(url: String) {
    val browserIntent =
      Intent(this, BrowserActivity::class.java).apply {
        putExtra(BrowserActivity.EXTRA_URL, url)
      }
    startActivity(browserIntent)
    finish()
  }
}

internal fun isWebUrl(text: String): Boolean {
  val trimmed = text.trim()
  if (trimmed.contains(Regex("\\s"))) return false
  return trimmed.startsWith("http://") || trimmed.startsWith("https://")
}

private val SUPPORTED_DOCUMENT_URL_EXTENSIONS =
  setOf(
    "pdf", "odt", "epub",
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "tiff", "tif", "avif",
  )

internal fun isSupportedDocumentUrl(url: String): Boolean {
  val path =
    try {
      java.net.URI(url).path ?: return false
    } catch (_: Exception) {
      return false
    }
  val lastSlash = path.lastIndexOf('/')
  val lastSegment = if (lastSlash >= 0) path.substring(lastSlash + 1) else path
  val lastDot = lastSegment.lastIndexOf('.')
  if (lastDot <= 0) return false
  val ext = lastSegment.substring(lastDot + 1).lowercase()
  return ext in SUPPORTED_DOCUMENT_URL_EXTENSIONS
}
