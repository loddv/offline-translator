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
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.davidv.translator.ui.TranslatorViewModel
import dev.davidv.translator.ui.TranslatorViewModelFactory
import dev.davidv.translator.ui.screens.TranslatorApp
import dev.davidv.translator.ui.theme.TranslatorTheme

class ProcessTextActivity : ComponentActivity() {
  private var textToTranslate: String = ""
  private var launchMode: LaunchMode = LaunchMode.Normal
  private lateinit var viewModel: TranslatorViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleIntent(intent)

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

    setContent {
      TranslatorTheme {
        TranslatorApp(
          viewModel = viewModel,
          onClose = { finish() },
        )
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i("ProcessTextActivity", "cleaning up")
  }

  override fun onNewIntent(intent: Intent) {
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
      TapToTranslateNotification.ACTION_OPEN_POPUP -> {
        launchMode = LaunchMode.ReadonlyModal
        textToTranslate = ""
      }
      Intent.ACTION_TRANSLATE -> {
        val isReadonly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        Log.i("IntentMessage", "is RO: $isReadonly")
        launchMode = if (isReadonly) LaunchMode.ReadonlyModal else LaunchMode.ReadWriteModal(replyModalIntent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        textToTranslate = text ?: ""
      }
      Intent.ACTION_PROCESS_TEXT -> {
        val isReadonly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
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
