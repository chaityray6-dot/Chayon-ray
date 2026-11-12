package com.example.data.pdf

import android.graphics.BitmapFactory
import com.example.data.local.AnnotationEntity
import com.example.data.local.AnnotationToolType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class NormalizedPoint(val x: Float, val y: Float)

class PdfEditorEngine(private val pdfEngine: PdfEngine) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportAnnotatedAndFilledPdf(
        sourceFile: File,
        outputFile: File,
        annotations: List<AnnotationEntity>,
        formUpdates: Map<String, String> = emptyMap()
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(sourceFile).use { doc ->
            // Apply interactive AcroForm values if present
            if (formUpdates.isNotEmpty()) {
                val acroForm = doc.documentCatalog.acroForm
                if (acroForm != null) {
                    formUpdates.forEach { (fieldName, value) ->
                        runCatching {
                            acroForm.getField(fieldName)?.setValue(value)
                        }
                    }
                }
            }

            // Burn annotations per page
            val grouped = annotations.groupBy { it.pageNumber }
            grouped.forEach { (pageIndex, pageAnnotations) ->
                if (pageIndex !in 0 until doc.numberOfPages) return@forEach
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox
                val pageW = box.width
                val pageH = box.height

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (ann in pageAnnotations) {
                        val r = ((ann.colorArgb shr 16) and 0xFF).toInt()
                        val g = ((ann.colorArgb shr 8) and 0xFF).toInt()
                        val b = (ann.colorArgb and 0xFF).toInt()
                        val type = runCatching { AnnotationToolType.valueOf(ann.toolType) }.getOrNull() ?: continue

                        val pts = runCatching {
                            json.decodeFromString<List<NormalizedPoint>>(ann.pointsJson)
                        }.getOrDefault(emptyList())

                        when (type) {
                            AnnotationToolType.HIGHLIGHT -> {
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.36f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(r, g, b)
                                if (pts.size >= 2) {
                                    val minX = pts.minOf { it.x } * pageW
                                    val maxX = pts.maxOf { it.x } * pageW
                                    val avgY = (1f - pts.map { it.y }.average().toFloat()) * pageH
                                    val h = (ann.strokeWidth * 3.5f).coerceIn(10f, 34f)
                                    cs.addRect(minX, avgY - h / 2f, (maxX - minX).coerceAtLeast(12f), h)
                                    cs.fill()
                                } else {
                                    val x = ann.xNorm * pageW
                                    val y = (1f - ann.yNorm - ann.heightNorm) * pageH
                                    cs.addRect(x, y, ann.widthNorm * pageW, ann.heightNorm * pageH)
                                    cs.fill()
                                }
                            }
                            AnnotationToolType.UNDERLINE, AnnotationToolType.STRIKETHROUGH -> {
                                val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 0.95f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(ann.strokeWidth.coerceIn(1.5f, 6f))
                                if (pts.size >= 2) {
                                    val x1 = pts.first().x * pageW
                                    val x2 = pts.last().x * pageW
                                    val y = (1f - pts.map { it.y }.average().toFloat()) * pageH
                                    cs.moveTo(x1, y)
                                    cs.lineTo(x2, y)
                                    cs.stroke()
                                }
                            }
                            AnnotationToolType.INK -> {
                                if (pts.size >= 2) {
                                    val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1f }
                                    cs.setGraphicsStateParameters(gs)
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth(ann.strokeWidth.coerceIn(1f, 14f))
                                    cs.moveTo(pts.first().x * pageW, (1f - pts.first().y) * pageH)
                                    for (i in 1 until pts.size) {
                                        cs.lineTo(pts[i].x * pageW, (1f - pts[i].y) * pageH)
                                    }
                                    cs.stroke()
                                }
                            }
                            AnnotationToolType.RECTANGLE -> {
                                val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(ann.strokeWidth.coerceIn(1.5f, 8f))
                                if (pts.size >= 2) {
                                    val x1 = pts.first().x * pageW
                                    val y1 = (1f - pts.first().y) * pageH
                                    val x2 = pts.last().x * pageW
                                    val y2 = (1f - pts.last().y) * pageH
                                    cs.addRect(minOf(x1, x2), minOf(y1, y2), kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
                                    cs.stroke()
                                }
                            }
                            AnnotationToolType.CIRCLE -> {
                                val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(ann.strokeWidth.coerceIn(1.5f, 8f))
                                if (pts.size >= 2) {
                                    val cx = ((pts.first().x + pts.last().x) / 2f) * pageW
                                    val cy = (1f - (pts.first().y + pts.last().y) / 2f) * pageH
                                    val rad = kotlin.math.hypot(
                                        (pts.last().x - pts.first().x) * pageW,
                                        (pts.last().y - pts.first().y) * pageH
                                    ) / 2f
                                    drawCircleApprox(cs, cx, cy, rad.coerceAtLeast(8f))
                                }
                            }
                            AnnotationToolType.ARROW -> {
                                val gs = PDExtendedGraphicsState().apply { strokingAlphaConstant = 1f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setStrokingColor(r, g, b)
                                cs.setLineWidth(ann.strokeWidth.coerceIn(1.5f, 8f))
                                if (pts.size >= 2) {
                                    val x1 = pts.first().x * pageW
                                    val y1 = (1f - pts.first().y) * pageH
                                    val x2 = pts.last().x * pageW
                                    val y2 = (1f - pts.last().y) * pageH
                                    cs.moveTo(x1, y1)
                                    cs.lineTo(x2, y2)
                                    val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                                    val headLen = 12.0
                                    cs.lineTo(
                                        (x2 - headLen * cos(angle - 0.45)).toFloat(),
                                        (y2 - headLen * sin(angle - 0.45)).toFloat()
                                    )
                                    cs.moveTo(x2, y2)
                                    cs.lineTo(
                                        (x2 - headLen * cos(angle + 0.45)).toFloat(),
                                        (y2 - headLen * sin(angle + 0.45)).toFloat()
                                    )
                                    cs.stroke()
                                }
                            }
                            AnnotationToolType.TEXT_BOX -> {
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 1f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(r, g, b)
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                                cs.newLineAtOffset(ann.xNorm * pageW, (1f - ann.yNorm) * pageH)
                                cs.showText(pdfEngine.sanitizeAsciiForType1(ann.textContent))
                                cs.endText()
                            }
                            AnnotationToolType.STICKY_NOTE -> {
                                val nx = ann.xNorm * pageW
                                val ny = (1f - ann.yNorm) * pageH
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.92f }
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(254, 240, 138)
                                cs.addRect(nx, ny - 44f, 140f, 44f)
                                cs.fill()
                                cs.setNonStrokingColor(30, 41, 59)
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, 9.5f)
                                cs.newLineAtOffset(nx + 6f, ny - 16f)
                                cs.showText(pdfEngine.sanitizeAsciiForType1(ann.textContent.take(28)))
                                cs.endText()
                            }
                            AnnotationToolType.SIGNATURE, AnnotationToolType.IMAGE -> {
                                val imgFile = File(ann.extraPath)
                                if (imgFile.exists()) {
                                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bmp != null) {
                                        val pdImg = LosslessFactory.createFromImage(doc, bmp)
                                        val w = (ann.widthNorm * pageW).coerceAtLeast(60f)
                                        val h = (ann.heightNorm * pageH).coerceAtLeast(35f)
                                        val x = ann.xNorm * pageW
                                        val y = (1f - ann.yNorm) * pageH - h
                                        cs.drawImage(pdImg, x, y, w, h)
                                        bmp.recycle()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            doc.save(outputFile)
        }
        pdfEngine.invalidateCacheFor(outputFile.absolutePath)
        outputFile
    }

    private fun drawCircleApprox(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = 0.5522847498f * r
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.stroke()
    }
}
