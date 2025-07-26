package dev.davidv.translator

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint


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
    val margin = 16
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
        println("start ${blockText}")
        val translated: String = translate(blockText)
        println("end")
        paint.textSize = blockAvgPixelHeight
        textPaint.textSize = blockAvgPixelHeight

        val translatedSpaceIndices = translated.mapIndexedNotNull { index, char ->
            if (char == ' ') index else null
        }
        allTranslatedText = "${allTranslatedText}\n${translated}"

        // 5% padding for word wrap
        val totalBBLength = textBlock.lines.sumOf { line -> line.boundingBox.width() } * 0.95f

        // Ensure text will fit the existing area
        while (textPaint.measureText(translated) >= totalBBLength && textPaint.textSize > 8) {
            textPaint.textSize -= 1
        }

        var start = 0
        textBlock.lines.forEach { line ->
            val fg = removeTextWithSmartBlur(canvas, mutableBitmap, line.boundingBox, backgroundMode)
            textPaint.color = fg
            // FIXME: the color should be set in the next loop, but we need some kind of lookup
            // to store it. for now this assumes that last FG color will be applied to entire block
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