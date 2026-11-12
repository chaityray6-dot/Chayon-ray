package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.PDFRenderer as PdfBoxRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

enum class PageSizePreset(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4", PDRectangle.A4.width, PDRectangle.A4.height),
    LETTER("Letter", PDRectangle.LETTER.width, PDRectangle.LETTER.height),
    LEGAL("Legal", PDRectangle.LEGAL.width, PDRectangle.LEGAL.height)
}

enum class CompressionLevel(val label: String, val dpi: Float, val jpegQuality: Int) {
    LOW("Low (High Quality)", 160f, 88),
    MEDIUM("Medium (Balanced)", 115f, 70),
    HIGH("High (Smallest Size)", 78f, 48)
}

enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    DIAGONAL("Diagonal"),
    TOP_LEFT("Top Left"),
    BOTTOM_RIGHT("Bottom Right"),
    TILED("Tiled Grid")
}

data class TocEntry(
    val title: String,
    val pageIndex: Int,
    val level: Int
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchCount: Int
)

data class AcroFormFieldInfo(
    val fullyQualifiedName: String,
    val currentValue: String,
    val isReadOnly: Boolean
)

class PdfEngine(private val context: Context) {

    private val renderMutex = Mutex()
    private val bitmapCache = object : LruCache<String, Bitmap>(28) {}

    fun invalidateCacheFor(filePath: String) {
        val snapshot = bitmapCache.snapshot()
        snapshot.keys.filter { it.startsWith("$filePath#") }.forEach { bitmapCache.remove(it) }
    }

    suspend fun getPageCount(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        if (password.isNullOrEmpty()) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer -> renderer.pageCount }
                }
            }.getOrElse {
                PDDocument.load(file, password ?: "").use { doc -> doc.numberOfPages }
            }
        } else {
            PDDocument.load(file, password).use { doc -> doc.numberOfPages }
        }
    }

    suspend fun isPasswordProtected(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext false
        runCatching {
            PDDocument.load(file).use { it.isEncrypted }
        }.getOrElse { true }
    }

    suspend fun renderPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val cacheKey = "${file.absolutePath}#${file.lastModified()}#$pageIndex#$targetWidthPx"
        bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }

        renderMutex.withLock {
            bitmapCache.get(cacheKey)?.let { if (!it.isRecycled) return@withContext it }
            val rendered = if (password.isNullOrEmpty()) {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            if (pageIndex !in 0 until renderer.pageCount) return@use null
                            renderer.openPage(pageIndex).use { page ->
                                val scale = targetWidthPx.toFloat() / page.width.coerceAtLeast(1)
                                val w = targetWidthPx.coerceAtLeast(100)
                                val h = (page.height * scale).toInt().coerceAtLeast(100)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }.getOrNull()
            } else null

            val finalBitmap = rendered ?: runCatching {
                PDDocument.load(file, password ?: "").use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@use null
                    val boxRenderer = PdfBoxRenderer(doc)
                    val pageWidthPt = doc.getPage(pageIndex).mediaBox.width.coerceAtLeast(1f)
                    val scale = targetWidthPx / pageWidthPt
                    boxRenderer.renderImage(pageIndex, scale)
                }
            }.getOrNull()

            if (finalBitmap != null) {
                bitmapCache.put(cacheKey, finalBitmap)
            }
            finalBitmap
        }
    }

    suspend fun extractPageText(file: File, pageIndex: Int, password: String = ""): String =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext ""
            runCatching {
                PDDocument.load(file, password).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@use ""
                    PDFTextStripper().apply {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }.getText(doc).trim()
                }
            }.getOrDefault("")
        }

    suspend fun extractAllPagesText(file: File, password: String = ""): List<String> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                PDDocument.load(file, password).use { doc ->
                    val stripper = PDFTextStripper()
                    (0 until doc.numberOfPages).map { index ->
                        stripper.startPage = index + 1
                        stripper.endPage = index + 1
                        stripper.getText(doc).trim()
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun searchInPdf(file: File, query: String, password: String = ""): List<PdfSearchMatch> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty() || !file.exists()) return@withContext emptyList()
            val matches = mutableListOf<PdfSearchMatch>()
            runCatching {
                PDDocument.load(file, password).use { doc ->
                    val stripper = PDFTextStripper()
                    for (i in 0 until doc.numberOfPages) {
                        stripper.startPage = i + 1
                        stripper.endPage = i + 1
                        val text = stripper.getText(doc)
                        val idx = text.indexOf(trimmed, ignoreCase = true)
                        if (idx >= 0) {
                            var count = 0
                            var pos = idx
                            while (pos >= 0) {
                                count++
                                pos = text.indexOf(trimmed, pos + trimmed.length, ignoreCase = true)
                            }
                            val start = (idx - 35).coerceAtLeast(0)
                            val end = (idx + trimmed.length + 55).coerceAtMost(text.length)
                            val snippet = text.substring(start, end).replace('\n', ' ').trim()
                            matches.add(PdfSearchMatch(i, "...$snippet...", count))
                        }
                    }
                }
            }
            matches
        }

    suspend fun extractTableOfContents(file: File, password: String = ""): List<TocEntry> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            val result = mutableListOf<TocEntry>()
            runCatching {
                PDDocument.load(file, password).use { doc ->
                    val outline = doc.documentCatalog.documentOutline ?: return@use
                    fun walk(item: PDOutlineItem?, level: Int) {
                        var current = item
                        while (current != null) {
                            val destPage = runCatching { current.findDestinationPage(doc) }.getOrNull()
                            val pageIdx = if (destPage != null) doc.pages.indexOf(destPage).coerceAtLeast(0) else 0
                            result.add(TocEntry(current.title ?: "Section", pageIdx, level))
                            walk(current.firstChild, level + 1)
                            current = current.nextSibling
                        }
                    }
                    walk(outline.firstChild, 0)
                }
            }
            result
        }

    suspend fun extractAcroFormFields(file: File, password: String = ""): List<AcroFormFieldInfo> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching {
                PDDocument.load(file, password).use { doc ->
                    val acroForm = doc.documentCatalog.acroForm ?: return@use emptyList()
                    acroForm.fieldTree.mapNotNull { field ->
                        val name = field.fullyQualifiedName ?: return@mapNotNull null
                        AcroFormFieldInfo(
                            fullyQualifiedName = name,
                            currentValue = field.valueAsString.orEmpty(),
                            isReadOnly = field.isReadOnly
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: PageSizePreset,
        landscape: Boolean,
        marginPt: Float
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val rect = if (landscape) {
                PDRectangle(pageSize.heightPt, pageSize.widthPt)
            } else {
                PDRectangle(pageSize.widthPt, pageSize.heightPt)
            }
            for (uri in imageUris) {
                val bitmap = decodeSampledBitmap(uri, 2000, 2600) ?: continue
                val page = PDPage(rect)
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.88f)
                val availW = (rect.width - marginPt * 2).coerceAtLeast(40f)
                val availH = (rect.height - marginPt * 2).coerceAtLeast(40f)
                val scale = minOf(availW / bitmap.width, availH / bitmap.height)
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val x = (rect.width - drawW) / 2f
                val y = (rect.height - drawH) / 2f
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, x, y, drawW, drawH)
                }
                bitmap.recycle()
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun bitmapsToPdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        pageSize: PageSizePreset = PageSizePreset.A4,
        marginPt: Float = 18f
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val rect = PDRectangle(pageSize.widthPt, pageSize.heightPt)
            for (bitmap in bitmaps) {
                val page = PDPage(rect)
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(doc, bitmap, 0.9f)
                val availW = rect.width - marginPt * 2
                val availH = rect.height - marginPt * 2
                val scale = minOf(availW / bitmap.width, availH / bitmap.height)
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val x = (rect.width - drawW) / 2f
                val y = (rect.height - drawH) / 2f
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, x, y, drawW, drawH)
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun textToPdf(
        title: String,
        bodyText: String,
        outputFile: File,
        pageSize: PageSizePreset = PageSizePreset.A4
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val rect = PDRectangle(pageSize.widthPt, pageSize.heightPt)
            val margin = 52f
            val maxWidth = rect.width - margin * 2
            val font = PDType1Font.HELVETICA
            val boldFont = PDType1Font.HELVETICA_BOLD
            val fontSize = 11.5f
            val leading = 17f

            val lines = wrapTextForPdf(bodyText, font, fontSize, maxWidth)
            var lineIdx = 0
            var pageNum = 1

            while (lineIdx < lines.size || pageNum == 1) {
                val page = PDPage(rect)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    var y = rect.height - margin
                    if (pageNum == 1 && title.isNotBlank()) {
                        cs.beginText()
                        cs.setFont(boldFont, 18f)
                        cs.newLineAtOffset(margin, y)
                        cs.showText(sanitizeAsciiForType1(title))
                        cs.endText()
                        y -= 30f
                    }
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(margin, y)
                    while (lineIdx < lines.size && y > margin + 20f) {
                        cs.showText(sanitizeAsciiForType1(lines[lineIdx]))
                        cs.newLineAtOffset(0f, -leading)
                        y -= leading
                        lineIdx++
                    }
                    cs.endText()

                    // Footer
                    cs.beginText()
                    cs.setFont(font, 9f)
                    cs.newLineAtOffset(rect.width - margin - 45f, 26f)
                    cs.showText("Page $pageNum")
                    cs.endText()
                }
                pageNum++
                if (lineIdx >= lines.size) break
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun mergePdfs(inputFiles: List<File>, outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val merger = PDFMergerUtility()
        merger.destinationFileName = outputFile.absolutePath
        inputFiles.forEach { if (it.exists()) merger.addSource(it) }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        outputFile
    }

    suspend fun splitPdf(inputFile: File, selectedPagesZeroBased: List<Int>, outputFile: File): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(inputFile).use { src ->
                PDDocument().use { dest ->
                    for (idx in selectedPagesZeroBased) {
                        if (idx in 0 until src.numberOfPages) {
                            dest.importPage(src.getPage(idx))
                        }
                    }
                    dest.save(outputFile)
                }
            }
            outputFile
        }

    suspend fun organizePages(
        inputFile: File,
        pageSpecs: List<Pair<Int, Int>>, // Pair(sourcePageIndexZeroBased, rotationDegreesDelta)
        extraInsertFile: File? = null,
        insertAfterPos: Int = -1,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { src ->
            val extraDoc = extraInsertFile?.takeIf { it.exists() }?.let { PDDocument.load(it) }
            try {
                PDDocument().use { dest ->
                    pageSpecs.forEachIndexed { pos, (pageIdx, rotDelta) ->
                        if (pageIdx in 0 until src.numberOfPages) {
                            val imported = dest.importPage(src.getPage(pageIdx))
                            imported.rotation = (imported.rotation + rotDelta) % 360
                        }
                        if (pos == insertAfterPos && extraDoc != null) {
                            for (eIdx in 0 until extraDoc.numberOfPages) {
                                dest.importPage(extraDoc.getPage(eIdx))
                            }
                        }
                    }
                    dest.save(outputFile)
                }
            } finally {
                extraDoc?.close()
            }
        }
        outputFile
    }

    suspend fun compressPdf(inputFile: File, outputFile: File, level: CompressionLevel): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(inputFile).use { src ->
                val renderer = PdfBoxRenderer(src)
                PDDocument().use { dest ->
                    val scale = level.dpi / 72f
                    for (i in 0 until src.numberOfPages) {
                        val origRect = src.getPage(i).mediaBox
                        val bmp = renderer.renderImage(i, scale)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                        bmp.recycle()
                        val compressedBmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())
                        val page = PDPage(origRect)
                        dest.addPage(page)
                        val pdImg = JPEGFactory.createFromImage(dest, compressedBmp, level.jpegQuality / 100f)
                        compressedBmp.recycle()
                        PDPageContentStream(dest, page).use { cs ->
                            cs.drawImage(pdImg, 0f, 0f, origRect.width, origRect.height)
                        }
                    }
                    dest.save(outputFile)
                }
            }
            outputFile
        }

    suspend fun protectPdf(inputFile: File, outputFile: File, userPassword: String): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(inputFile).use { doc ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(userPassword, userPassword, ap)
                spp.encryptionKeyLength = 128
                doc.protect(spp)
                doc.save(outputFile)
            }
            outputFile
        }

    suspend fun unlockPdf(inputFile: File, outputFile: File, password: String): File =
        withContext(Dispatchers.IO) {
            outputFile.parentFile?.mkdirs()
            PDDocument.load(inputFile, password).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                doc.save(outputFile)
            }
            outputFile
        }

    suspend fun addTextWatermark(
        inputFile: File,
        outputFile: File,
        text: String,
        opacity: Float,
        position: WatermarkPosition
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile).use { doc ->
            val font = PDType1Font.HELVETICA_BOLD
            val safeText = sanitizeAsciiForType1(text)
            for (page in doc.pages) {
                val rect = page.mediaBox
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 1f)
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(80, 95, 165)

                    when (position) {
                        WatermarkPosition.CENTER -> {
                            cs.beginText()
                            cs.setFont(font, 36f)
                            cs.setTextMatrix(Matrix.getTranslateInstance(rect.width * 0.22f, rect.height * 0.5f))
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.DIAGONAL -> {
                            cs.beginText()
                            cs.setFont(font, 40f)
                            cs.setTextMatrix(
                                Matrix.getRotateInstance(Math.toRadians(38.0), rect.width * 0.2f, rect.height * 0.3f)
                            )
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.TOP_LEFT -> {
                            cs.beginText()
                            cs.setFont(font, 20f)
                            cs.newLineAtOffset(36f, rect.height - 48f)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.BOTTOM_RIGHT -> {
                            cs.beginText()
                            cs.setFont(font, 20f)
                            cs.newLineAtOffset(rect.width - 210f, 36f)
                            cs.showText(safeText)
                            cs.endText()
                        }
                        WatermarkPosition.TILED -> {
                            for (row in 1..3) {
                                for (col in 0..1) {
                                    cs.beginText()
                                    cs.setFont(font, 24f)
                                    cs.setTextMatrix(
                                        Matrix.getRotateInstance(
                                            Math.toRadians(30.0),
                                            60f + col * (rect.width * 0.45f),
                                            row * (rect.height * 0.25f)
                                        )
                                    )
                                    cs.showText(safeText)
                                    cs.endText()
                                }
                            }
                        }
                    }
                }
            }
            doc.save(outputFile)
        }
        outputFile
    }

    suspend fun addImageWatermark(
        inputFile: File,
        watermarkImageUri: Uri,
        outputFile: File,
        opacity: Float,
        position: WatermarkPosition
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val bmp = decodeSampledBitmap(watermarkImageUri, 512, 512) ?: return@withContext inputFile
        PDDocument.load(inputFile).use { doc ->
            val pdImg = LosslessFactory.createFromImage(doc, bmp)
            for (page in doc.pages) {
                val rect = page.mediaBox
                val w = 130f
                val h = w * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
                val (x, y) = when (position) {
                    WatermarkPosition.TOP_LEFT -> 32f to (rect.height - h - 32f)
                    WatermarkPosition.BOTTOM_RIGHT -> (rect.width - w - 32f) to 32f
                    else -> ((rect.width - w) / 2f) to ((rect.height - h) / 2f)
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    val gs = PDExtendedGraphicsState().apply {
                        nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 1f)
                    }
                    cs.setGraphicsStateParameters(gs)
                    cs.drawImage(pdImg, x, y, w, h)
                }
            }
            doc.save(outputFile)
        }
        bmp.recycle()
        outputFile
    }

    fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val resolver = context.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var inSampleSize = 1
        while (opts.outWidth / inSampleSize > reqWidth || opts.outHeight / inSampleSize > reqHeight) {
            inSampleSize *= 2
        }
        val loadOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, loadOpts) }
    }

    private fun wrapTextForPdf(
        text: String,
        font: PDType1Font,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = text.replace("\r\n", "\n").split("\n")
        for (para in paragraphs) {
            if (para.isBlank()) {
                result.add("")
                continue
            }
            val words = para.split(" ")
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                val w = runCatching {
                    font.getStringWidth(sanitizeAsciiForType1(candidate)) / 1000f * fontSize
                }.getOrDefault(candidate.length * fontSize * 0.52f)
                if (w > maxWidth && line.isNotEmpty()) {
                    result.add(line.toString())
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(candidate)
                }
            }
            if (line.isNotEmpty()) result.add(line.toString())
        }
        return result
    }

    internal fun sanitizeAsciiForType1(input: String): String {
        return input.map { ch ->
            if (ch.code in 32..126) ch else if (ch == '\t') ' ' else '?'
        }.joinToString("")
    }
}
