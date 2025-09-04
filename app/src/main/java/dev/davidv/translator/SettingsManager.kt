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

class SettingsManager(
  context: Context,
) {
  private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

  // Track which settings have been explicitly modified - initialize first
  private val modifiedSettings = mutableSetOf<String>()

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private fun loadSettings(): AppSettings {
    val defaults = AppSettings() // Get current defaults

    // Build set of modified settings from SharedPreferences
    modifiedSettings.clear()
    prefs.all.keys.forEach { key ->
      modifiedSettings.add(key)
    }

    val defaultTargetLanguageCode = prefs.getString("default_target_language", null)
    val defaultTargetLanguage =
      if (defaultTargetLanguageCode != null) {
        Language.entries.find { it.code == defaultTargetLanguageCode } ?: defaults.defaultTargetLanguage
      } else {
        defaults.defaultTargetLanguage
      }

    val translationModelsBaseUrl =
      prefs.getString("translation_models_base_url_v2", null)
        ?: defaults.translationModelsBaseUrl

    val tesseractModelsBaseUrl =
      prefs.getString("tesseract_models_base_url", null)
        ?: defaults.tesseractModelsBaseUrl

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

    val minConfidence =
      if (prefs.contains("min_confidence")) {
        prefs.getInt("min_confidence", defaults.minConfidence)
      } else {
        defaults.minConfidence
      }

    val maxImageSize =
      if (prefs.contains("max_image_size")) {
        prefs.getInt("max_image_size", defaults.maxImageSize)
      } else {
        defaults.maxImageSize
      }

    val disableOcr =
      if (prefs.contains("disable_ocr")) {
        prefs.getBoolean("disable_ocr", defaults.disableOcr)
      } else {
        defaults.disableOcr
      }

    val disableCLD =
      if (prefs.contains("disable_cld")) {
        prefs.getBoolean("disable_cld", defaults.disableCLD)
      } else {
        defaults.disableCLD
      }

    val disableTransliteration =
      if (prefs.contains("disable_transliteration")) {
        prefs.getBoolean("disable_transliteration", defaults.disableTransliteration)
      } else {
        defaults.disableTransliteration
      }

    val useExternalStorage =
      if (prefs.contains("use_external_storage")) {
        prefs.getBoolean("use_external_storage", defaults.useExternalStorage)
      } else {
        defaults.useExternalStorage
      }

    val fontSize =
      if (prefs.contains("font_factor")) {
        try {
          prefs.getFloat("font_factor", defaults.fontFactor)
        } catch (_: ClassCastException) {
          defaults.fontFactor
        }
      } else {
        defaults.fontFactor
      }

    return AppSettings(
      defaultTargetLanguage = defaultTargetLanguage,
      translationModelsBaseUrl = translationModelsBaseUrl,
      tesseractModelsBaseUrl = tesseractModelsBaseUrl,
      backgroundMode = backgroundMode,
      minConfidence = minConfidence,
      maxImageSize = maxImageSize,
      disableOcr = disableOcr,
      disableCLD = disableCLD,
      disableTransliteration = disableTransliteration,
      useExternalStorage = useExternalStorage,
      fontFactor = fontSize,
    )
  }

  fun updateSettings(newSettings: AppSettings) {
    val currentSettings = _settings.value

    prefs.edit().apply {
      // Only save settings that have changed from their current value
      if (newSettings.defaultTargetLanguage != currentSettings.defaultTargetLanguage) {
        putString("default_target_language", newSettings.defaultTargetLanguage.code)
        modifiedSettings.add("default_target_language")
      }
      if (newSettings.translationModelsBaseUrl != currentSettings.translationModelsBaseUrl) {
        putString("translation_models_base_url_v2", newSettings.translationModelsBaseUrl)
        modifiedSettings.add("translation_models_base_url_v2")
      }
      if (newSettings.tesseractModelsBaseUrl != currentSettings.tesseractModelsBaseUrl) {
        putString("tesseract_models_base_url", newSettings.tesseractModelsBaseUrl)
        modifiedSettings.add("tesseract_models_base_url")
      }
      if (newSettings.backgroundMode != currentSettings.backgroundMode) {
        putString("background_mode", newSettings.backgroundMode.name)
        modifiedSettings.add("background_mode")
      }
      if (newSettings.minConfidence != currentSettings.minConfidence) {
        putInt("min_confidence", newSettings.minConfidence)
        modifiedSettings.add("min_confidence")
      }
      if (newSettings.maxImageSize != currentSettings.maxImageSize) {
        putInt("max_image_size", newSettings.maxImageSize)
        modifiedSettings.add("max_image_size")
      }
      if (newSettings.disableOcr != currentSettings.disableOcr) {
        putBoolean("disable_ocr", newSettings.disableOcr)
        modifiedSettings.add("disable_ocr")
      }
      if (newSettings.disableCLD != currentSettings.disableCLD) {
        putBoolean("disable_cld", newSettings.disableCLD)
        modifiedSettings.add("disable_cld")
      }
      if (newSettings.disableTransliteration != currentSettings.disableTransliteration) {
        putBoolean("disable_transliteration", newSettings.disableTransliteration)
        modifiedSettings.add("disable_transliteration")
      }
      if (newSettings.useExternalStorage != currentSettings.useExternalStorage) {
        putBoolean("use_external_storage", newSettings.useExternalStorage)
        modifiedSettings.add("use_external_storage")
      }
      if (newSettings.fontFactor != currentSettings.fontFactor) {
        putFloat("font_factor", newSettings.fontFactor)
        modifiedSettings.add("font_factor")
      }
      apply()
    }
    _settings.value = newSettings
  }
}
