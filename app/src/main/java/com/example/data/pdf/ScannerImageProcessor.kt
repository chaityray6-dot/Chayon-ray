package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ScanFilterMode(val label: String) {
    ORIGINAL("Original"),
    CLEAR_TEXT("Clear Text HD"),
    MAGIC_COLOR("Magic Color"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("B&W Document")
}

data class ScanQuad(
    val topLeft: PointF = PointF(0.06f, 0.06f),
    val topRight: PointF = PointF(0.94f, 0.06f),
    val bottomRight: PointF = PointF(0.94f, 0.94f),
    val bottomLeft: PointF = PointF(0.06f, 0.94f)
)

object ScannerImageProcessor {

    /**
     * Estimates document bounds by scanning luminance gradients inward from edges.
     */
    fun detectDocumentBounds(bitmap: Bitmap): ScanQuad {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 32 || h < 32) return ScanQuad()

        val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        var minX = 127
        var maxX = 0
        var minY = 127
        var maxY = 0
        val centerPixel = scaled.getPixel(64, 64)
        val centerLum = luminance(centerPixel)

        for (y in 4 until 124) {
            for (x in 4 until 124) {
                val lum = luminance(scaled.getPixel(x, y))
                if (abs(lum - centerLum) < 42f && lum > 110f) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }
        if (scaled != bitmap) scaled.recycle()

        if (maxX - minX < 35 || maxY - minY < 35) return ScanQuad()
        val l = (minX / 128f).coerceIn(0.03f, 0.3f)
        val r = (maxX / 128f).coerceIn(0.7f, 0.97f)
        val t = (minY / 128f).coerceIn(0.03f, 0.3f)
        val b = (maxY / 128f).coerceIn(0.7f, 0.97f)
        return ScanQuad(
            topLeft = PointF(l, t),
            topRight = PointF(r, t),
            bottomRight = PointF(r, b),
            bottomLeft = PointF(l, b)
        )
    }

    fun applyPerspectiveCrop(source: Bitmap, quad: ScanQuad): Bitmap {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()

        val tl = PointF(quad.topLeft.x * sw, quad.topLeft.y * sh)
        val tr = PointF(quad.topRight.x * sw, quad.topRight.y * sh)
        val br = PointF(quad.bottomRight.x * sw, quad.bottomRight.y * sh)
        val bl = PointF(quad.bottomLeft.x * sw, quad.bottomLeft.y * sh)

        val topW = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val bottomW = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val leftH = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightH = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()

        val targetW = max(200, max(topW, bottomW).toInt()).coerceAtMost(2400)
        val targetH = max(200, max(leftH, rightH).toInt()).coerceAtMost(3200)

        val srcPts = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPts = floatArrayOf(
            0f, 0f,
            targetW.toFloat(), 0f,
            targetW.toFloat(), targetH.toFloat(),
            0f, targetH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun cropAndRotateBitmap(
        source: Bitmap,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float,
        rotationDegrees: Int = 0
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val l = leftNorm.coerceIn(0f, 0.92f)
        val t = topNorm.coerceIn(0f, 0.92f)
        val r = rightNorm.coerceIn(l + 0.05f, 1f)
        val b = bottomNorm.coerceIn(t + 0.05f, 1f)

        val x = (l * rotated.width).toInt().coerceIn(0, rotated.width - 1)
        val y = (t * rotated.height).toInt().coerceIn(0, rotated.height - 1)
        val w = ((r - l) * rotated.width).toInt().coerceIn(1, rotated.width - x)
        val h = ((b - t) * rotated.height).toInt().coerceIn(1, rotated.height - y)

        val cropped = Bitmap.createBitmap(rotated, x, y, w, h)
        if (rotated !== source && cropped !== rotated) {
            rotated.recycle()
        }
        return cropped
    }

    fun applyEnhancement(
        source: Bitmap,
        filter: ScanFilterMode,
        brightness: Float = 0f, // -100f..100f
        contrast: Float = 1f,   // 0.5f..2.0f
        sharpen: Float = 0f     // 0.0f..1.5f
    ): Bitmap {
        val colorFiltered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(colorFiltered)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        when (filter) {
            ScanFilterMode.ORIGINAL -> Unit
            ScanFilterMode.CLEAR_TEXT -> {
                // Boost crispness of text ink while brightening faded paper background
                val c = 1.65f * contrast
                val t = (-0.5f * c + 0.5f) * 255f + 22f + brightness
                val boost = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(boost)
            }
            ScanFilterMode.MAGIC_COLOR -> {
                val sat = ColorMatrix().apply { setSaturation(1.25f) }
                val c = 1.38f * contrast
                val t = (-0.5f * c + 0.5f) * 255f + 18f + brightness
                val boost = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sat)
                cm.postConcat(boost)
            }
            ScanFilterMode.GRAYSCALE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(gray)
            }
            ScanFilterMode.BLACK_AND_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val c = 2.6f * contrast
                val t = (-0.5f * c + 0.5f) * 255f + 24f + brightness
                val bw = ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(gray)
                cm.postConcat(bw)
            }
        }

        if (filter == ScanFilterMode.ORIGINAL || filter == ScanFilterMode.GRAYSCALE) {
            val t = (-0.5f * contrast + 0.5f) * 255f + brightness
            val bc = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t,
                    0f, contrast, 0f, 0f, t,
                    0f, 0f, contrast, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(bc)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        val effectiveSharpen = when {
            filter == ScanFilterMode.CLEAR_TEXT -> max(sharpen, 0.85f)
            else -> sharpen
        }
        return if (effectiveSharpen > 0.02f) {
            val sharpened = applySharpenAndClarity(
                source = colorFiltered,
                sharpenStrength = effectiveSharpen,
                denoise = filter == ScanFilterMode.CLEAR_TEXT
            )
            if (sharpened !== colorFiltered) colorFiltered.recycle()
            sharpened
        } else {
            colorFiltered
        }
    }

    /**
     * Unsharp mask sharpening with subtle speckle-noise threshold to clarify blurry/soft document text.
     */
    fun applySharpenAndClarity(
        source: Bitmap,
        sharpenStrength: Float = 0.8f,
        denoise: Boolean = true
    ): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 3 || h < 3 || sharpenStrength <= 0.02f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val amount = sharpenStrength.coerceIn(0f, 2.0f)
        val total = w * h
        val src = IntArray(total)
        val dst = IntArray(total)
        source.getPixels(src, 0, w, 0, 0, w, h)

        // Copy border pixels directly
        System.arraycopy(src, 0, dst, 0, w)
        System.arraycopy(src, (h - 1) * w, dst, (h - 1) * w, w)

        val threshold = if (denoise) 5 else 0
        for (y in 1 until h - 1) {
            val rowOffset = y * w
            dst[rowOffset] = src[rowOffset]
            dst[rowOffset + w - 1] = src[rowOffset + w - 1]

            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val center = src[idx]
                val top = src[idx - w]
                val bottom = src[idx + w]
                val left = src[idx - 1]
                val right = src[idx + 1]
                val tl = src[idx - w - 1]
                val tr = src[idx - w + 1]
                val bl = src[idx + w - 1]
                val br = src[idx + w + 1]

                val cR = (center shr 16) and 0xFF
                val cG = (center shr 8) and 0xFF
                val cB = center and 0xFF

                val avgR = (
                    ((center shr 16) and 0xFF) * 2 +
                        ((top shr 16) and 0xFF) + ((bottom shr 16) and 0xFF) +
                        ((left shr 16) and 0xFF) + ((right shr 16) and 0xFF) +
                        ((tl shr 16) and 0xFF) + ((tr shr 16) and 0xFF) +
                        ((bl shr 16) and 0xFF) + ((br shr 16) and 0xFF)
                    ) / 10

                val avgG = (
                    ((center shr 8) and 0xFF) * 2 +
                        ((top shr 8) and 0xFF) + ((bottom shr 8) and 0xFF) +
                        ((left shr 8) and 0xFF) + ((right shr 8) and 0xFF) +
                        ((tl shr 8) and 0xFF) + ((tr shr 8) and 0xFF) +
                        ((bl shr 8) and 0xFF) + ((br shr 8) and 0xFF)
                    ) / 10

                val avgB = (
                    (center and 0xFF) * 2 +
                        (top and 0xFF) + (bottom and 0xFF) +
                        (left and 0xFF) + (right and 0xFF) +
                        (tl and 0xFF) + (tr and 0xFF) +
                        (bl and 0xFF) + (br and 0xFF)
                    ) / 10

                val dR = cR - avgR
                val dG = cG - avgG
                val dB = cB - avgB

                val outR = if (abs(dR) > threshold) (cR + (dR * amount).toInt()).coerceIn(0, 255) else cR
                val outG = if (abs(dG) > threshold) (cG + (dG * amount).toInt()).coerceIn(0, 255) else cG
                val outB = if (abs(dB) > threshold) (cB + (dB * amount).toInt()).coerceIn(0, 255) else cB

                dst[idx] = (center and -0x1000000) or (outR shl 16) or (outG shl 8) or outB
            }
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(dst, 0, w, 0, 0, w, h)
        }
    }

    private fun luminance(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
