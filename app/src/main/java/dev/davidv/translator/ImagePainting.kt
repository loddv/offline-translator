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

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.util.Log
import kotlin.system.measureTimeMillis


fun getForegroundColorByContrast(bitmap: Bitmap, textBounds: Rect, backgroundColor: Int): Int {
    val bgLuminance = getLuminance(backgroundColor)

    val pixels = IntArray(textBounds.width() * textBounds.height())
    bitmap.getPixels(pixels, 0, textBounds.width(), textBounds.left, textBounds.top, textBounds.width(), textBounds.height())

    return pixels.maxByOrNull { pixel ->
        getColorContrast(pixel, bgLuminance)
    } ?: Color.BLACK
}

fun getColorContrast(color1: Int, bgLuminance: Float): Float {
    val lum = getLuminance(color1)

    val brighter = maxOf(lum, bgLuminance)
    val darker = minOf(lum, bgLuminance)

    return (brighter + 0.05f) / (darker + 0.05f)
}

fun getLuminance(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f

    return 0.299f * r + 0.587f * g + 0.114f * b
}


fun removeTextWithSmartBlur(canvas: Canvas, bitmap: Bitmap, textBounds: Rect, backgroundMode: BackgroundMode = BackgroundMode.AUTO_DETECT): Int {
    // Determine background and foreground colors based on mode
    val (surroundingColor, fgColor) = when (backgroundMode) {
        BackgroundMode.WHITE_ON_BLACK -> Pair(Color.BLACK, Color.WHITE)
        BackgroundMode.BLACK_ON_WHITE -> Pair(Color.WHITE, Color.BLACK)
        BackgroundMode.AUTO_DETECT -> {
            // Get average color from area around text (not inside text)
            val detectedSurroundingColor = getSurroundingAverageColor(bitmap, textBounds)
            val detectedFgColor = getForegroundColorByContrast(bitmap, textBounds, detectedSurroundingColor)
            Pair(detectedSurroundingColor, detectedFgColor)
        }
    }
    var paint = Paint().apply {
        color = surroundingColor
    }

    canvas.drawRect(textBounds, paint)

    paint = Paint().apply {
        color = surroundingColor
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        alpha = 128
        isAntiAlias = true
    }

    canvas.drawRect(textBounds, paint)
    return fgColor
}

fun getSurroundingAverageColor(bitmap: Bitmap, textBounds: Rect): Int {
    val margin = 4
    val sampleRegions = listOf(
        // Left side
        Rect(maxOf(0, textBounds.left - margin), textBounds.top,
            textBounds.left, textBounds.bottom),
        // Right side
        Rect(textBounds.right, textBounds.top,
            minOf(bitmap.width, textBounds.right + margin), textBounds.bottom),
        // Top
        Rect(textBounds.left, maxOf(0, textBounds.top - margin),
            textBounds.right, textBounds.top),
        // Bottom
        Rect(textBounds.left, textBounds.bottom,
            textBounds.right, minOf(bitmap.height, textBounds.bottom + margin))
    )

    var totalR = 0L
    var totalG = 0L
    var totalB = 0L
    var totalCount = 0

    for (region in sampleRegions) {
        if (region.width() == 0 || region.height() == 0) {
            continue
        }

        val pixels = IntArray(region.width() * region.height())
        bitmap.getPixels(pixels, 0, region.width(), region.left, region.top, region.width(), region.height())
        for (pixel in pixels) {
            totalR += Color.red(pixel)
            totalG += Color.green(pixel)
            totalB += Color.blue(pixel)
            totalCount++
        }
    }

    return if (totalCount > 0) {
        Color.rgb(
            (totalR / totalCount).toInt(),
            (totalG / totalCount).toInt(),
            (totalB / totalCount).toInt()
        )
    } else {
        Color.WHITE
    }
}

suspend fun paintTranslatedTextOver(
    originalBitmap: Bitmap,
    textBlocks: Array<TextBlock>,
    translate: suspend (String) -> String,
    backgroundMode: BackgroundMode = BackgroundMode.AUTO_DETECT
): Pair<Bitmap, String> {
    val mutableBitmap = originalBitmap.copy(originalBitmap.config, true)
    val canvas = Canvas(mutableBitmap)

    val textPaint = TextPaint().apply {
        isAntiAlias = true
    }

    var allTranslatedText = ""
    var totalTranslateMs: Long = 0
    
    val textSizePadding = 0.95f
    val minTextSize = 8f
    
    textBlocks.forEach { textBlock ->
        val blockAvgPixelHeight =
            textBlock.lines.map { textLine -> textLine.boundingBox.height() }.average().toFloat()
        val blockText = textBlock.lines.joinToString(" ") { line -> line.text }
        
        val translated: String
        totalTranslateMs += measureTimeMillis {
            translated = translate(blockText)
        }

        val translatedSpaceIndices = translated.mapIndexedNotNull { index, char ->
            if (char == ' ') index else null
        }
        allTranslatedText = "${allTranslatedText}\n${translated}"

        val totalBBLength = textBlock.lines.sumOf { line -> line.boundingBox.width() }
        val availableBBLength = totalBBLength * textSizePadding

        textPaint.textSize = blockAvgPixelHeight
        while (textPaint.measureText(translated) >= availableBBLength && textPaint.textSize > minTextSize) {
            textPaint.textSize -= 1f
        }

        // Store colors for each line to avoid redundant calculations
        val lineColors = mutableMapOf<Int, Int>()
        textBlock.lines.forEachIndexed { index, line ->
            val fg = removeTextWithSmartBlur(canvas, mutableBitmap, line.boundingBox, backgroundMode)
            lineColors[index] = fg
        }

        var start = 0
        textBlock.lines.forEachIndexed { lineIndex, line ->
            // Set color for this specific line
            lineColors[lineIndex]?.let { color ->
                textPaint.color = color
            }
            
            if (start < translated.length) {
                val measuredWidth = FloatArray(1)
                val countedChars = textPaint.breakText(
                    translated, start, translated.length, true,
                    line.boundingBox.width().toFloat(), measuredWidth
                )

                val endIndex: Int = if (start + countedChars == translated.length) {
                    translated.length
                } else {
                    val previousSpaceIndex = translatedSpaceIndices.findLast { it < start + countedChars }
                    previousSpaceIndex?.let { it + 1 } ?: (start + countedChars)
                }

                canvas.drawText(
                    translated,
                    start,
                    endIndex,
                    line.boundingBox.left.toFloat(),
                    line.boundingBox.top.toFloat() - textPaint.ascent(),
                    textPaint
                )
                start = endIndex
            }
        }
    }

    Log.i("ImagePainting", "Translation took ${totalTranslateMs}ms")
    return Pair(mutableBitmap, allTranslatedText.trim())
}