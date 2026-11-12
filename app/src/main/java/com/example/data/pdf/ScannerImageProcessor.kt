package com.example.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ScanFilterMode(val label: String) {
    ORIGINAL("Original"),
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
                if (kotlin.math.abs(lum - centerLum) < 42f && lum > 110f) {
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

    fun applyEnhancement(
        source: Bitmap,
        filter: ScanFilterMode,
        brightness: Float = 0f, // -100f..100f
        contrast: Float = 1f    // 0.5f..2.0f
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        when (filter) {
            ScanFilterMode.ORIGINAL -> Unit
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
        return output
    }

    private fun luminance(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
