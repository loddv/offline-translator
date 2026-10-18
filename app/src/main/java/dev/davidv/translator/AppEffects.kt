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
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import java.util.Locale

// The native server takes a config snapshot at start, so anything it serves from settings
// restarts it.
private data class HttpServerSettings(
  val enabled: Boolean,
  val port: Int,
  val bindMode: HttpServerBindMode,
  val maxImageSize: Int,
  val minConfidence: Int,
  val backgroundMode: BackgroundMode,
  val translatePdfImages: Boolean,
)

private data class TtsVoiceData(
  val locales: List<Locale>,
  val voices: List<TranslatorTtsVoice>,
  val overrides: Map<String, String>,
)

fun launchAppEffects(
  scope: CoroutineScope,
  context: Context,
  settingsManager: SettingsManager,
  catalogRepository: CatalogRepository,
) {
  val settings = settingsManager.settings

  // A default naming a deleted language would keep seeding every surface with a pair that can't
  // translate; English is the pivot every catalog carries.
  scope.launch {
    catalogRepository.catalog
      .map(LanguageAvailabilityState::of)
      .filter { it.hasLanguages }
      .collect { availability ->
        val uninstalled =
          availability.availableLanguages
            .filterNot { it.availability.translatorFiles }
            .map { it.language.code }
            .toSet()
        settingsManager.update { current ->
          current.copy(
            defaultTargetLanguageCode = current.defaultTargetLanguageCode.takeUnless { it in uninstalled } ?: "en",
            defaultSourceLanguageCode = current.defaultSourceLanguageCode?.let { if (it in uninstalled) "en" else it },
          )
        }
      }
  }

  scope.launch(Dispatchers.IO) {
    settings
      .map { it.useExternalStorage }
      .distinctUntilChanged()
      .drop(1)
      .collect { catalogRepository.reloadIndex() }
  }

  scope.launch {
    settings
      .map { it.registerAsBrowser }
      .distinctUntilChanged()
      .collect { enabled ->
        val desired =
          if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
          } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
          }
        context.packageManager.setComponentEnabledSetting(
          ComponentName(context, "dev.davidv.translator.MainActivityBrowser"),
          desired,
          PackageManager.DONT_KILL_APP,
        )
      }
  }

  scope.launch {
    settings
      .map { it.tapToTranslateEnabled }
      .distinctUntilChanged()
      .collect { enabled ->
        if (enabled) {
          TapToTranslateNotification.show(context)
        } else {
          TapToTranslateNotification.hide(context)
        }
      }
  }

  scope.launch {
    settings
      .map {
        HttpServerSettings(
          enabled = it.httpServerEnabled,
          port = it.httpServerPort,
          bindMode = it.httpServerBindMode,
          maxImageSize = it.maxImageSize,
          minConfidence = it.minConfidence,
          backgroundMode = it.backgroundMode,
          translatePdfImages = it.translatePdfImages,
        )
      }.distinctUntilChanged()
      .withIndex()
      // Stopping goes through startService, which background-started processes may not call,
      // and at startup there is no running server to stop anyway.
      .filter { (index, server) -> index > 0 || server.enabled }
      .collect { (_, server) ->
        if (server.enabled) {
          TranslationApiService.start(context, server.port, server.bindMode)
        } else {
          TranslationApiService.stop(context)
        }
      }
  }

  scope.launch {
    combine(catalogRepository.catalog, settings.map { it.ttsVoiceOverrides }.distinctUntilChanged()) { catalog, overrides ->
      TtsVoiceData(
        locales = TranslatorTtsEngine.installedTtsLocales(catalog),
        voices = TranslatorTtsEngine.systemTtsVoices(catalog),
        overrides = overrides,
      )
    }.distinctUntilChanged()
      .drop(1)
      .collect { context.sendBroadcast(Intent(TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED)) }
  }
}
