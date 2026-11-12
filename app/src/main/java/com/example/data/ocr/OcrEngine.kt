package com.example.data.ocr

import android.graphics.Bitmap
import com.example.data.pdf.PdfEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OcrLanguage(val label: String, val code: String) {
    ENGLISH("English (On-Device ML Kit)", "en"),
    BENGALI("বাংলা / Bengali (Script Assist)", "bn")
}

data class OcrPageResult(
    val pageIndex: Int,
    val extractedText: String,
    val note: String? = null
)

class OcrEngine(private val pdfEngine: PdfEngine) {

    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeBitmap(bitmap: Bitmap, language: OcrLanguage): OcrPageResult =
        withContext(Dispatchers.Default) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognized = suspendCancellableCoroutine { cont ->
                latinRecognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result.text) }
                    .addOnFailureListener { err -> cont.resumeWithException(err) }
            }
            val note = if (language == OcrLanguage.BENGALI) {
                "On-device ML Kit Latin OCR ran locally. Full Bengali (বাংলা) script recognition requires bundling Tesseract ben.traineddata or enabling cloud AI; use the OCR Correction Editor below to refine Bengali text before exporting."
            } else null

            OcrPageResult(
                pageIndex = 0,
                extractedText = recognized.trim(),
                note = note
            )
        }

    suspend fun createSearchablePdfFromImages(
        pages: List<Pair<Bitmap, String>>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val font = PDType1Font.HELVETICA
            for ((bitmap, ocrText) in pages) {
                val rect = PDRectangle.A4
                val page = PDPage(rect)
                doc.addPage(page)
                val pdImg = JPEGFactory.createFromImage(doc, bitmap, 0.88f)
                val scale = minOf(rect.width / bitmap.width, rect.height / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val x = (rect.width - w) / 2f
                val y = (rect.height - h) / 2f

                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImg, x, y, w, h)
                    // Embed invisible searchable text layer
                    if (ocrText.isNotBlank()) {
                        cs.beginText()
                        cs.setFont(font, 10f)
                        cs.setRenderingMode(RenderingMode.NEITHER)
                        var textY = rect.height - 36f
                        cs.newLineAtOffset(24f, textY)
                        for (line in ocrText.lines()) {
                            if (textY < 24f) break
                            cs.showText(pdfEngine.sanitizeAsciiForType1(line.take(95)))
                            cs.newLineAtOffset(0f, -13f)
                            textY -= 13f
                        }
                        cs.endText()
                    }
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }
}
