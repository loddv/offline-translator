package com.example.translator

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint


fun getForegroundColorByContrast(bitmap: Bitmap, textBounds: Rect, backgroundColor: Int): Int {
    val textPixels = mutableListOf<Int>()

    // Sample pixels throughout the text region
    for (x in textBounds.left until textBounds.right step 2) {
        for (y in textBounds.top until textBounds.bottom step 2) {
            textPixels.add(bitmap.getPixel(x, y))
        }
    }

    // Find the color with highest contrast to background
    return textPixels.maxByOrNull { pixel ->
        getColorContrast(pixel, backgroundColor)
    } ?: Color.BLACK
}

fun getColorContrast(color1: Int, color2: Int): Float {
    val lum1 = getLuminance(color1)
    val lum2 = getLuminance(color2)

    val brighter = maxOf(lum1, lum2)
    val darker = minOf(lum1, lum2)

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

    val colors = mutableListOf<Int>()

    // TODO: bitmap.getPixels() instead of sampling
    for (region in sampleRegions) {
        if (region.width() > 0 && region.height() > 0) {
            // Sample every few pixels to avoid too much processing
            for (x in region.left until region.right step 3) {
                for (y in region.top until region.bottom step 3) {
                    colors.add(bitmap.getPixel(x, y))
                }
            }
        }
    }

    return averageColors(colors)
}

fun averageColors(colors: List<Int>): Int {
    if (colors.isEmpty()) return Color.WHITE

    var totalR = 0
    var totalG = 0
    var totalB = 0

    for (color in colors) {
        totalR += Color.red(color)
        totalG += Color.green(color)
        totalB += Color.blue(color)
    }

    return Color.rgb(
        totalR / colors.size,
        totalG / colors.size,
        totalB / colors.size
    )
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

    textBlocks.forEach { textBlock ->
        val blockAvgPixelHeight =
            textBlock.lines.map { textLine -> textLine.boundingBox.height() }.average().toFloat()
        val blockText = textBlock.lines.joinToString(" ") { line -> line.text }
        val translated = translate(blockText)

        paint.textSize = blockAvgPixelHeight
        textPaint.textSize = blockAvgPixelHeight

        println("textsize $blockAvgPixelHeight")

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
            val fg = removeTextWithSmartBlur(canvas, mutableBitmap, line.boundingBox)
//            val (fg, bg) = detectTextColors(mutableBitmap, line.boundingBox)
//            paint.color = bg // Color.WHITE
//            paint.alpha = 220 // bg blurs more ofc, but its confusing
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

    return Pair(mutableBitmap, allTranslatedText.trim())
}