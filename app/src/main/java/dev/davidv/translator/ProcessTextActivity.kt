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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.davidv.translator.ui.screens.TranslatorApp
import dev.davidv.translator.ui.theme.TranslatorTheme
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {
  private var textToTranslate: String = ""
  private var launchMode: LaunchMode = LaunchMode.Normal
  private lateinit var ocrService: OCRService
  private lateinit var translationCoordinator: TranslationCoordinator
  private lateinit var downloadService: DownloadService
  private lateinit var serviceConnection: ServiceConnection

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)

    val settingsManager = SettingsManager(this)
    val filePathManager = FilePathManager(this, settingsManager.settings)
    ocrService = OCRService(filePathManager)
    val imageProcessor = ImageProcessor(this, ocrService)
    val ctx = this

    serviceConnection =
      object : ServiceConnection {
        override fun onServiceConnected(
          name: ComponentName?,
          service: IBinder?,
        ) {
          val binder = service as DownloadService.DownloadBinder
          downloadService = binder.getService()

          lifecycleScope.launch {
            Log.d("ProcessTextActivity", "Initializing translation service")
            val translationService = TranslationService(settingsManager, filePathManager)
            val languageDetector = LanguageDetector()
            translationCoordinator = TranslationCoordinator(ctx, translationService, languageDetector, imageProcessor, settingsManager)

            setContent {
              TranslatorTheme {
                TranslatorApp(
                  initialText = textToTranslate,
                  sharedImageUri = null,
                  translationCoordinator = translationCoordinator,
                  settingsManager = settingsManager,
                  filePathManager = filePathManager,
                  downloadService = downloadService,
                  launchMode = launchMode,
                )
              }
            }
          }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
        }
      }

    val intent = Intent(this, DownloadService::class.java)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
  }

  override fun onDestroy() {
    super.onDestroy()
    ocrService.cleanup()
    TranslationService.cleanup()
    if (::serviceConnection.isInitialized) {
      unbindService(serviceConnection)
    }
    Log.i("ProcessTextActivity", "cleaning up")
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    val replyModalIntent = { text: String ->
      Log.i("IntentMessage", "sending response '$text' to intent")
      val respIntent = Intent()
      respIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, text)
      setResult(RESULT_OK, respIntent)
      finish()
    }

    when (intent?.action) {
      Intent.ACTION_PROCESS_TEXT -> {
        val isReadonly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        Log.i("IntentMessage", "is RO: $isReadonly")
        launchMode = if (isReadonly) LaunchMode.ReadonlyModal else LaunchMode.ReadWriteModal(replyModalIntent)
        val text =
          if (intent.hasExtra(Intent.EXTRA_PROCESS_TEXT)) {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
          } else {
            intent.getStringExtra(Intent.EXTRA_TEXT)
          }
        textToTranslate = text ?: ""
      }
    }
  }
}
