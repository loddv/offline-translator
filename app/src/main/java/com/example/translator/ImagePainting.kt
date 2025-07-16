package com.example.translator

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import kotlin.system.measureTimeMillis


fun getForegroundColorByContrast(bitmap: Bitmap, textBounds: Rect, backgroundColor: Int): Int {
    val textPixels = mutableListOf<Int>()

    // Get all pixels from the text region
    val pixels = IntArray(textBounds.width() * textBounds.height())
    bitmap.getPixels(pixels, 0, textBounds.width(), textBounds.left, textBounds.top, textBounds.width(), textBounds.height())
    textPixels.addAll(pixels.toList())

    val bgLuminance = getLuminance(backgroundColor)

    // Find the color with highest contrast to background
    return textPixels.maxByOrNull { pixel ->
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


fun removeTextWithSmartBlur(canvas: Canvas, bitmap: Bitmap, textBounds: Rect): Int {
    // Get average color from area around text (not inside text)
    val surroundingColor = getSurroundingAverageColor(bitmap, textBounds)
    val fgColor = getForegroundColorByContrast(bitmap, textBounds, surroundingColor)
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

fun paintTranslatedTextOver(
    originalBitmap: Bitmap,
    textBlocks: Array<TextBlock>,
    translate: (String) -> String,
): Pair<Bitmap, String> {
    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val paint = Paint().apply {
        style = Paint.Style.FILL
    }


    val textPaint = TextPaint()
    textPaint.color = Color.BLACK

    var allTranslatedText = ""

    var totalTranslateTime = 0L
    textBlocks.forEach { textBlock ->
        val blockAvgPixelHeight =
            textBlock.lines.map { textLine -> textLine.boundingBox.height() }.average().toFloat()
        val blockText = textBlock.lines.joinToString(" ") { line -> line.text }
        val translated: String
        totalTranslateTime += measureTimeMillis {
            translated = translate(blockText)
        }

        paint.textSize = blockAvgPixelHeight
        textPaint.textSize = blockAvgPixelHeight

        val translatedSpaceIndices = translated.mapIndexedNotNull { index, char ->
            if (char == ' ') index else null
        }
        allTranslatedText = "${allTranslatedText}\n${translated}"

        // 5% padding for word wrap
        val totalBBLength = textBlock.lines.sumOf { line -> line.boundingBox.width() } * 0.95f

        // Ensure text will fit the existing area
        while (textPaint.measureText(translated) >= totalBBLength) {
            textPaint.textSize -= 1
        }

        var start = 0
        textBlock.lines.forEach { line ->
            // TODO: add config for hardcoded fg/bg
            val fg = removeTextWithSmartBlur(canvas, mutableBitmap, line.boundingBox)
            textPaint.color = fg
        }
        textBlock.lines.forEach { line ->
            // Render text if we are not done.
            // We may be done when translated text takes fewer lines than the original
            // In which case, we just paint the background color over the previous text
            if (start < translated.length) {
                // How many chars can fit in this line?
                val measuredWidth = FloatArray(1)
                val countedChars = textPaint.breakText(
                    translated, start, translated.length, true,
                    line.boundingBox.width().toFloat(), measuredWidth
                )

                // If we are in the middle of a word, return up to the previous word
                val endIndex: Int
                if (start + countedChars == translated.length) {
                    endIndex = translated.length
                } else {
                    val previousSpaceIndex =
                        translatedSpaceIndices.findLast { it < start + countedChars }
                    endIndex = if (previousSpaceIndex == null) {
                        start + countedChars
                    } else {
                        previousSpaceIndex + 1
                    }
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
    println("Image translation took ${totalTranslateTime}ms")

    return Pair(mutableBitmap, allTranslatedText.trim())
}