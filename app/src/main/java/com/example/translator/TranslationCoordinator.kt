package com.example.translator

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranslationCoordinator(
    private val context: Context,
    private val translationService: TranslationService,
    private val languageDetector: LanguageDetector,
    private val imageProcessor: ImageProcessor
) {
    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()
    
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
        return languageDetector.detectLanguageEnum(text)
    }
    
    suspend fun translateImage(from: Language, to: Language, uri: Uri, onImageLoaded: (Bitmap) -> Unit): ProcessedImageResult? {
        _isTranslating.value = true
        return try {
            // Load and correct image orientation
            val bitmap = imageProcessor.loadBitmapFromUri(uri)
            val correctedBitmap = imageProcessor.correctImageOrientation(uri, bitmap)
            
            // Show original image immediately
            onImageLoaded(correctedBitmap)
            
            // Process image for OCR
            val processedImage = imageProcessor.processImage(correctedBitmap)
            
            // Extract text from image
            val extractedText = processedImage.textBlocks.map { block ->
                block.lines.map { line -> line.text }
            }.flatten().joinToString("\n")
            
            // Translate the extracted text
            val translatedText = when (val result = translationService.translate(from, to, extractedText)) {
                is TranslationResult.Success -> result.text
                is TranslationResult.Error -> {
                    Toast.makeText(context, "Translation error: ${result.message}", Toast.LENGTH_SHORT).show()
                    return null
                }
            }
            
            ProcessedImageResult(
                correctedBitmap = correctedBitmap,
                extractedText = extractedText,
                translatedText = translatedText
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Image processing error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            _isTranslating.value = false
        }
    }
    
    suspend fun translateImageWithOverlay(from: Language, to: Language, uri: Uri, onImageLoaded: (Bitmap) -> Unit): ProcessedImageResult? {
        _isTranslating.value = true
        return try {
            // Load and correct image orientation
            val bitmap = imageProcessor.loadBitmapFromUri(uri)
            val correctedBitmap = imageProcessor.correctImageOrientation(uri, bitmap)
            
            // Show original image immediately
            onImageLoaded(correctedBitmap)
            
            // Process image for OCR
            val processedImage = imageProcessor.processImage(correctedBitmap)
            
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
            
            // Paint translated text over image
            val (overlayBitmap, allTranslatedText) = paintTranslatedTextOver(
                processedImage.bitmap,
                processedImage.textBlocks,
                ::translateFn
            )
            
            ProcessedImageResult(
                correctedBitmap = overlayBitmap,
                extractedText = processedImage.textBlocks.map { block ->
                    block.lines.map { line -> line.text }
                }.flatten().joinToString("\n"),
                translatedText = allTranslatedText
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Image processing error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        } finally {
            _isTranslating.value = false
        }
    }
}

data class ProcessedImageResult(
    val correctedBitmap: Bitmap,
    val extractedText: String,
    val translatedText: String
)