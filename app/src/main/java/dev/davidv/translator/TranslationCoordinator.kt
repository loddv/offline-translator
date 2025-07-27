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
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranslationCoordinator(
    private val context: Context,
    private val translationService: TranslationService,
    private val languageDetector: LanguageDetector,
    private val imageProcessor: ImageProcessor,
    private val settingsManager: SettingsManager
) {
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()
    
    private val _isOcrInProgress = MutableStateFlow(false)
    val isOcrInProgress: StateFlow<Boolean> = _isOcrInProgress.asStateFlow()
    
    suspend fun translateText(from: Language, to: Language, text: String): String? {
        if (text.isBlank()) return ""
        
        _isTranslating.value = true
        return try {
            when (val result = translationService.translate(from, to, text)) {
                is TranslationResult.Success -> result.text
                is TranslationResult.Error -> {
                    Toast.makeText(context, "Translation error: ${result.message}", Toast.LENGTH_SHORT).show()
                    null
                }
            }
        } finally {
            _isTranslating.value = false
        }
    }
    
    suspend fun detectLanguage(text: String): Language? {
        return languageDetector.detectLanguage(text)
    }

    suspend fun translateImageWithOverlay(from: Language, to: Language, uri: Uri, onImageLoaded: (Bitmap) -> Unit): ProcessedImageResult? {
        _isTranslating.value = true
        return try {
            val bitmap = imageProcessor.loadBitmapFromUri(uri)
            val correctedBitmap = imageProcessor.correctImageOrientation(uri, bitmap)
            val maxImageSize = settingsManager.settings.value.maxImageSize
            val downscaledBitmap = imageProcessor.downscaleImage(correctedBitmap, maxImageSize)
            onImageLoaded(downscaledBitmap)
            
            _isOcrInProgress.value = true
            val minConfidence = settingsManager.settings.value.minConfidence
            val processedImage = imageProcessor.processImage(downscaledBitmap, minConfidence)
            _isOcrInProgress.value = false
            
            // Create translation function for overlay
            suspend fun translateFn(text: String): String {
                return when (val result = translationService.translate(from, to, text)) {
                    is TranslationResult.Success -> result.text
                    is TranslationResult.Error -> {
                        Toast.makeText(context, "Translation error: ${result.message}", Toast.LENGTH_SHORT).show()
                        "Error"
                    }
                }
            }

            Log.d("OCR", "complete, result ${processedImage.textBlocks}")
            // Paint translated text over image
            val (overlayBitmap, allTranslatedText) = paintTranslatedTextOver(
                processedImage.bitmap,
                processedImage.textBlocks,
                ::translateFn,
                settingsManager.settings.value.backgroundMode
            )
            
            ProcessedImageResult(
                correctedBitmap = overlayBitmap,
                extractedText = processedImage.textBlocks.map { block ->
                    block.lines.map { line -> line.text }
                }.flatten().joinToString("\n"),
                translatedText = allTranslatedText
            )
        } catch (e: Exception) {
            println(e.stackTrace)
            Toast.makeText(context, "Image processing error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            _isOcrInProgress.value = false
            _isTranslating.value = false
        }
    }
}

data class ProcessedImageResult(
    val correctedBitmap: Bitmap,
    val extractedText: String,
    val translatedText: String
)