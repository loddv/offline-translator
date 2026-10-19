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
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SettingsManager(
  context: Context,
) {
  private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

  // Serializes read-modify-write with its persist so concurrent updates can't reach prefs in a
  // different order than they reached the flow.
  private val lock = Any()

  init {
    prefs
      .edit()
      .remove("translation_models_base_url_v3")
      .remove("tesseract_models_base_url")
      .remove("dictionary_base_url")
      .apply()
  }

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private fun loadSettings(): AppSettings {
    val defaults = AppSettings()

    val defaultTargetLanguageCode = prefs.getString("default_target_language", null) ?: defaults.defaultTargetLanguageCode
    val defaultSourceLanguageCode = prefs.getString("default_source_language", null)

    val catalogIndexUrl =
      prefs.getString("catalog_index_url", null)
        ?: defaults.catalogIndexUrl

    val backgroundModeName = prefs.getString("background_mode", null)
    val backgroundMode =
      if (backgroundModeName != null) {
        try {
          BackgroundMode.valueOf(backgroundModeName)
        } catch (_: IllegalArgumentException) {
          defaults.backgroundMode
        }
      } else {
        defaults.backgroundMode
      }

    val minConfidence = prefs.getInt("min_confidence", defaults.minConfidence)
    val maxImageSize = prefs.getInt("max_image_size", defaults.maxImageSize).coerceIn(600, 2000)
    val disableCLD = prefs.getBoolean("disable_cld", defaults.disableCLD)
    val experimentalScreenTranslate =
      prefs.getBoolean("experimental_screen_translate", defaults.experimentalScreenTranslate)
    val enableOutputTransliteration = prefs.getBoolean("enable_output_transliteration", defaults.enableOutputTransliteration)
    val useExternalStorage = prefs.getBoolean("use_external_storage", defaults.useExternalStorage)
    val fontFactor = prefs.getFloat("font_factor", defaults.fontFactor)
    val showTransliterationOnInput = prefs.getBoolean("show_transliteration_on_input", defaults.showTransliterationOnInput)
    val onlyShowOutputOnReadonlyModal =
      prefs.getBoolean(
        "only_show_output_on_readonly_modal",
        defaults.onlyShowOutputOnReadonlyModal,
      )
    val readonlyModalOutputAlignmentName = prefs.getString("readonly_modal_output_alignment", null)
    val readonlyModalOutputAlignment =
      if (readonlyModalOutputAlignmentName != null) {
        try {
          ReadonlyModalOutputAlignment.valueOf(readonlyModalOutputAlignmentName)
        } catch (_: IllegalArgumentException) {
          defaults.readonlyModalOutputAlignment
        }
      } else {
        defaults.readonlyModalOutputAlignment
      }
    val readonlyPopupSize =
      ReadonlyPopupSize.fromSliderPosition(
        prefs.getFloat("readonly_modal_compact_height_factor", defaults.readonlyPopupSize.sliderPosition),
      ) ?: defaults.readonlyPopupSize
    val mainInputSplit =
      InputSplit.parse(prefs.getFloat("main_input_split", defaults.mainInputSplit.fraction))
        ?: defaults.mainInputSplit
    val addSpacesForJapaneseTransliteration =
      prefs.getBoolean(
        "add_spaces_for_japanese_transliteration",
        defaults.addSpacesForJapaneseTransliteration,
      )
    val ttsPlaybackSpeed = prefs.getFloat("tts_playback_speed", defaults.ttsPlaybackSpeed)
    val ttsPlaybackSpeedOverrides =
      prefs
        .getString("tts_playback_speed_overrides", null)
        ?.let(::parsePlaybackSpeedOverrides)
        ?: defaults.ttsPlaybackSpeedOverrides
    val tapToTranslateEnabled = prefs.getBoolean("tap_to_translate_enabled", defaults.tapToTranslateEnabled)
    val ttsVoiceOverrides =
      prefs
        .getString("tts_voice_overrides", null)
        ?.let(::parseVoiceOverrides)
        ?: defaults.ttsVoiceOverrides
    val ttsReadUrlsAndHashtags =
      prefs.getBoolean("tts_read_urls_and_hashtags", defaults.ttsReadUrlsAndHashtags)
    val clearWebTranslatorDataOnClose =
      prefs.getBoolean(
        "clear_web_translator_data_on_close",
        defaults.clearWebTranslatorDataOnClose,
      )
    val translatePdfImages =
      prefs.getBoolean("translate_pdf_images", defaults.translatePdfImages)
    val liveCameraOverlayEnabled =
      prefs.getBoolean("live_camera_overlay_enabled", defaults.liveCameraOverlayEnabled)
    val registerAsBrowser =
      prefs.getBoolean("register_as_browser", defaults.registerAsBrowser)
    val multiTargetEnabled =
      prefs.getBoolean("multi_target_enabled", defaults.multiTargetEnabled)
    val httpServerEnabled = prefs.getBoolean("http_server_enabled", defaults.httpServerEnabled)
    val httpServerPort = prefs.getInt("http_server_port", defaults.httpServerPort).coerceIn(1, 65535)
    val httpServerBindModeName = prefs.getString("http_server_bind_mode", null)
    val httpServerBindMode =
      if (httpServerBindModeName != null) {
        try {
          HttpServerBindMode.valueOf(httpServerBindModeName)
        } catch (_: IllegalArgumentException) {
          defaults.httpServerBindMode
        }
      } else {
        defaults.httpServerBindMode
      }
    val assistantActionName = prefs.getString("assistant_action", null)
    val assistantAction =
      if (assistantActionName != null) {
        try {
          AssistantAction.valueOf(assistantActionName)
        } catch (_: IllegalArgumentException) {
          defaults.assistantAction
        }
      } else {
        defaults.assistantAction
      }

    return AppSettings(
      defaultTargetLanguageCode = defaultTargetLanguageCode,
      defaultSourceLanguageCode = defaultSourceLanguageCode,
      catalogIndexUrl = catalogIndexUrl,
      backgroundMode = backgroundMode,
      minConfidence = minConfidence,
      maxImageSize = maxImageSize,
      disableCLD = disableCLD,
      experimentalScreenTranslate = experimentalScreenTranslate,
      enableOutputTransliteration = enableOutputTransliteration,
      useExternalStorage = useExternalStorage,
      fontFactor = fontFactor,
      showTransliterationOnInput = showTransliterationOnInput,
      onlyShowOutputOnReadonlyModal = onlyShowOutputOnReadonlyModal,
      readonlyModalOutputAlignment = readonlyModalOutputAlignment,
      readonlyPopupSize = readonlyPopupSize,
      mainInputSplit = mainInputSplit,
      addSpacesForJapaneseTransliteration = addSpacesForJapaneseTransliteration,
      ttsPlaybackSpeed = ttsPlaybackSpeed,
      ttsPlaybackSpeedOverrides = ttsPlaybackSpeedOverrides,
      ttsVoiceOverrides = ttsVoiceOverrides,
      ttsReadUrlsAndHashtags = ttsReadUrlsAndHashtags,
      tapToTranslateEnabled = tapToTranslateEnabled,
      clearWebTranslatorDataOnClose = clearWebTranslatorDataOnClose,
      translatePdfImages = translatePdfImages,
      liveCameraOverlayEnabled = liveCameraOverlayEnabled,
      registerAsBrowser = registerAsBrowser,
      assistantAction = assistantAction,
      multiTargetEnabled = multiTargetEnabled,
      httpServerEnabled = httpServerEnabled,
      httpServerPort = httpServerPort,
      httpServerBindMode = httpServerBindMode,
    )
  }

  fun update(transform: (AppSettings) -> AppSettings) {
    synchronized(lock) {
      val currentSettings = _settings.value
      val newSettings = transform(currentSettings)
      if (newSettings == currentSettings) return
      persist(currentSettings, newSettings)
      _settings.value = newSettings
    }
  }

  // Only changed keys are written, so settings the user never touched stay absent and keep
  // following AppSettings defaults.
  private fun persist(
    currentSettings: AppSettings,
    newSettings: AppSettings,
  ) {
    prefs.edit().apply {
      if (newSettings.defaultTargetLanguageCode != currentSettings.defaultTargetLanguageCode) {
        putString("default_target_language", newSettings.defaultTargetLanguageCode)
      }
      if (newSettings.defaultSourceLanguageCode != currentSettings.defaultSourceLanguageCode) {
        if (newSettings.defaultSourceLanguageCode != null) {
          putString("default_source_language", newSettings.defaultSourceLanguageCode)
        } else {
          remove("default_source_language")
        }
      }
      if (newSettings.catalogIndexUrl != currentSettings.catalogIndexUrl) {
        putString("catalog_index_url", newSettings.catalogIndexUrl)
      }
      if (newSettings.backgroundMode != currentSettings.backgroundMode) {
        putString("background_mode", newSettings.backgroundMode.name)
      }
      if (newSettings.minConfidence != currentSettings.minConfidence) {
        putInt("min_confidence", newSettings.minConfidence)
      }
      if (newSettings.maxImageSize != currentSettings.maxImageSize) {
        putInt("max_image_size", newSettings.maxImageSize)
      }
      if (newSettings.disableCLD != currentSettings.disableCLD) {
        putBoolean("disable_cld", newSettings.disableCLD)
      }
      if (newSettings.experimentalScreenTranslate != currentSettings.experimentalScreenTranslate) {
        putBoolean("experimental_screen_translate", newSettings.experimentalScreenTranslate)
      }
      if (newSettings.enableOutputTransliteration != currentSettings.enableOutputTransliteration) {
        putBoolean("enable_output_transliteration", newSettings.enableOutputTransliteration)
      }
      if (newSettings.useExternalStorage != currentSettings.useExternalStorage) {
        putBoolean("use_external_storage", newSettings.useExternalStorage)
      }
      if (newSettings.fontFactor != currentSettings.fontFactor) {
        putFloat("font_factor", newSettings.fontFactor)
      }
      if (newSettings.showTransliterationOnInput != currentSettings.showTransliterationOnInput) {
        putBoolean("show_transliteration_on_input", newSettings.showTransliterationOnInput)
      }
      if (newSettings.onlyShowOutputOnReadonlyModal != currentSettings.onlyShowOutputOnReadonlyModal) {
        putBoolean("only_show_output_on_readonly_modal", newSettings.onlyShowOutputOnReadonlyModal)
      }
      if (newSettings.readonlyModalOutputAlignment != currentSettings.readonlyModalOutputAlignment) {
        putString("readonly_modal_output_alignment", newSettings.readonlyModalOutputAlignment.name)
      }
      if (newSettings.readonlyPopupSize != currentSettings.readonlyPopupSize) {
        putFloat("readonly_modal_compact_height_factor", newSettings.readonlyPopupSize.sliderPosition)
      }
      if (newSettings.mainInputSplit != currentSettings.mainInputSplit) {
        putFloat("main_input_split", newSettings.mainInputSplit.fraction)
      }
      if (newSettings.addSpacesForJapaneseTransliteration != currentSettings.addSpacesForJapaneseTransliteration) {
        putBoolean("add_spaces_for_japanese_transliteration", newSettings.addSpacesForJapaneseTransliteration)
      }
      if (newSettings.ttsPlaybackSpeed != currentSettings.ttsPlaybackSpeed) {
        putFloat("tts_playback_speed", newSettings.ttsPlaybackSpeed)
      }
      if (newSettings.ttsPlaybackSpeedOverrides != currentSettings.ttsPlaybackSpeedOverrides) {
        putString("tts_playback_speed_overrides", serializePlaybackSpeedOverrides(newSettings.ttsPlaybackSpeedOverrides))
      }
      if (newSettings.ttsVoiceOverrides != currentSettings.ttsVoiceOverrides) {
        putString("tts_voice_overrides", serializeVoiceOverrides(newSettings.ttsVoiceOverrides))
      }
      if (newSettings.ttsReadUrlsAndHashtags != currentSettings.ttsReadUrlsAndHashtags) {
        putBoolean("tts_read_urls_and_hashtags", newSettings.ttsReadUrlsAndHashtags)
      }
      if (newSettings.tapToTranslateEnabled != currentSettings.tapToTranslateEnabled) {
        putBoolean("tap_to_translate_enabled", newSettings.tapToTranslateEnabled)
      }
      if (newSettings.clearWebTranslatorDataOnClose != currentSettings.clearWebTranslatorDataOnClose) {
        putBoolean("clear_web_translator_data_on_close", newSettings.clearWebTranslatorDataOnClose)
      }
      if (newSettings.translatePdfImages != currentSettings.translatePdfImages) {
        putBoolean("translate_pdf_images", newSettings.translatePdfImages)
      }
      if (newSettings.liveCameraOverlayEnabled != currentSettings.liveCameraOverlayEnabled) {
        putBoolean("live_camera_overlay_enabled", newSettings.liveCameraOverlayEnabled)
      }
      if (newSettings.registerAsBrowser != currentSettings.registerAsBrowser) {
        putBoolean("register_as_browser", newSettings.registerAsBrowser)
      }
      if (newSettings.assistantAction != currentSettings.assistantAction) {
        putString("assistant_action", newSettings.assistantAction.name)
      }
      if (newSettings.multiTargetEnabled != currentSettings.multiTargetEnabled) {
        putBoolean("multi_target_enabled", newSettings.multiTargetEnabled)
      }
      if (newSettings.httpServerEnabled != currentSettings.httpServerEnabled) {
        putBoolean("http_server_enabled", newSettings.httpServerEnabled)
      }
      if (newSettings.httpServerPort != currentSettings.httpServerPort) {
        putInt("http_server_port", newSettings.httpServerPort)
      }
      if (newSettings.httpServerBindMode != currentSettings.httpServerBindMode) {
        putString("http_server_bind_mode", newSettings.httpServerBindMode.name)
      }
      apply()
    }
  }

  private fun parseVoiceOverrides(json: String): Map<String, String> =
    runCatching {
      val root = JSONObject(json)
      root.keys().asSequence().mapNotNull { languageCode ->
        root.optString(languageCode).takeIf { it.isNotBlank() }?.let { languageCode to it }
      }.toMap()
    }.getOrDefault(emptyMap())

  private fun serializeVoiceOverrides(overrides: Map<String, String>): String {
    val root = JSONObject()
    overrides.toSortedMap().forEach { (languageCode, voiceName) ->
      root.put(languageCode, voiceName)
    }
    return root.toString()
  }

  private fun parsePlaybackSpeedOverrides(json: String): Map<String, Float> =
    runCatching {
      val root = JSONObject(json)
      root.keys().asSequence().mapNotNull { voiceName ->
        val speed = root.optDouble(voiceName, Double.NaN)
        if (speed.isNaN()) {
          null
        } else {
          voiceName to speed.toFloat().coerceIn(0.5f, 2.0f)
        }
      }.toMap()
    }.getOrDefault(emptyMap())

  private fun serializePlaybackSpeedOverrides(overrides: Map<String, Float>): String {
    val root = JSONObject()
    overrides.toSortedMap().forEach { (voiceName, speed) ->
      root.put(voiceName, speed.coerceIn(0.5f, 2.0f))
    }
    return root.toString()
  }
}
