package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.converter.OfficeConverterEngine
import com.example.data.converter.OfficeFormat
import com.example.data.local.AnnotationDao
import com.example.data.local.AnnotationEntity
import com.example.data.local.BookmarkDao
import com.example.data.local.BookmarkEntity
import com.example.data.local.ChatDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentType
import com.example.data.local.SignatureDao
import com.example.data.local.SignatureEntity
import com.example.data.pdf.PdfEngine
import com.example.data.security.VaultSecurityManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val annotationDao: AnnotationDao,
    private val signatureDao: SignatureDao,
    private val chatDao: ChatDao,
    private val pdfEngine: PdfEngine,
    private val officeConverterEngine: OfficeConverterEngine,
    private val vaultSecurityManager: VaultSecurityManager
) {
    val documentsDir: File = File(context.filesDir, "documents").apply { mkdirs() }
    val vaultDir: File = File(context.filesDir, "vault").apply { mkdirs() }
    val signaturesDir: File = File(context.filesDir, "signatures").apply { mkdirs() }
    val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.observeAllVisibleDocuments()
    val vaultedDocuments: Flow<List<DocumentEntity>> = documentDao.observeVaultedDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = documentDao.observeRecentlyOpened()
    val starredDocuments: Flow<List<DocumentEntity>> = documentDao.observeStarredDocuments()
    val savedSignatures: Flow<List<SignatureEntity>> = signatureDao.observeAll()

    fun observeDocument(id: Long): Flow<DocumentEntity?> = documentDao.observeById(id)
    suspend fun getDocument(id: Long): DocumentEntity? = documentDao.getById(id)

    fun observeBookmarks(docId: Long): Flow<List<BookmarkEntity>> = bookmarkDao.observeForDocument(docId)
    fun observeAnnotations(docId: Long): Flow<List<AnnotationEntity>> = annotationDao.observeForDocument(docId)
    suspend fun getAnnotationsSnapshot(docId: Long): List<AnnotationEntity> = annotationDao.getSnapshotForDocument(docId)
    fun observeChat(docId: Long): Flow<List<ChatMessageEntity>> = chatDao.observeForDocument(docId)

    suspend fun toggleStar(id: Long, starred: Boolean) = documentDao.setStarred(id, starred)

    suspend fun recordOpen(id: Long, page: Int) =
        documentDao.updateReadProgress(id, System.currentTimeMillis(), page)

    suspend fun renameDocument(id: Long, newTitle: String) =
        documentDao.rename(id, newTitle.trim())

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        File(doc.filePath).delete()
        documentDao.deleteById(doc.id)
    }

    suspend fun addBookmark(docId: Long, pageNumber: Int, label: String) {
        bookmarkDao.insert(BookmarkEntity(documentId = docId, pageNumber = pageNumber, label = label))
    }

    suspend fun removeBookmarkAtPage(docId: Long, pageNumber: Int) {
        bookmarkDao.deleteAtPage(docId, pageNumber)
    }

    suspend fun addAnnotation(annotation: AnnotationEntity): Long = annotationDao.insert(annotation)
    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteById(id)
    suspend fun clearPageAnnotations(docId: Long, page: Int) = annotationDao.clearPage(docId, page)

    suspend fun saveSignature(signature: SignatureEntity): Long = signatureDao.insert(signature)
    suspend fun deleteSignature(id: Long) = signatureDao.deleteById(id)

    suspend fun insertChatMessage(msg: ChatMessageEntity): Long = chatDao.insert(msg)
    suspend fun clearChat(docId: Long) = chatDao.clearForDocument(docId)

    suspend fun importDocumentFromUri(uri: Uri): DocumentEntity = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Imported_Document_${System.currentTimeMillis()}.pdf"
        val mime = context.contentResolver.getType(uri) ?: "application/pdf"
        val ext = displayName.substringAfterLast('.', "pdf").lowercase()

        val targetPdfFile = uniqueFileIn(documentsDir, displayName.substringBeforeLast('.') + ".pdf")

        val docType = when (ext) {
            "docx" -> {
                officeConverterEngine.convertOfficeUriToPdf(uri, displayName, OfficeFormat.DOCX, targetPdfFile)
                DocumentType.DOCX
            }
            "xlsx" -> {
                officeConverterEngine.convertOfficeUriToPdf(uri, displayName, OfficeFormat.XLSX, targetPdfFile)
                DocumentType.XLSX
            }
            "pptx" -> {
                officeConverterEngine.convertOfficeUriToPdf(uri, displayName, OfficeFormat.PPTX, targetPdfFile)
                DocumentType.PPTX
            }
            "txt" -> {
                officeConverterEngine.convertOfficeUriToPdf(uri, displayName, OfficeFormat.TXT, targetPdfFile)
                DocumentType.TXT
            }
            else -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetPdfFile.outputStream().use { out -> input.copyTo(out) }
                }
                DocumentType.PDF
            }
        }

        registerPdfFile(
            file = targetPdfFile,
            title = displayName.substringBeforeLast('.'),
            docType = docType,
            mimeType = mime
        )
    }

    suspend fun registerPdfFile(
        file: File,
        title: String,
        docType: DocumentType = DocumentType.PDF,
        mimeType: String = "application/pdf",
        folderCategory: String = "All"
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val protected = pdfEngine.isPasswordProtected(file)
        val pages = if (protected) 1 else pdfEngine.getPageCount(file).coerceAtLeast(1)
        val hash = sha256File(file)
        val entity = DocumentEntity(
            title = title,
            filePath = file.absolutePath,
            mimeType = mimeType,
            docType = docType.name,
            fileSizeBytes = file.length(),
            pageCount = pages,
            isPasswordProtected = protected,
            contentHash = hash,
            folderCategory = folderCategory
        )
        val id = documentDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun moveToVault(doc: DocumentEntity): DocumentEntity = withContext(Dispatchers.IO) {
        val src = File(doc.filePath)
        val dest = File(vaultDir, "${doc.id}_${System.currentTimeMillis()}.dvault")
        vaultSecurityManager.encryptFileToVault(src, dest)
        src.delete()
        documentDao.updateVaultState(doc.id, vaulted = true, newPath = dest.absolutePath)
        doc.copy(isVaulted = true, filePath = dest.absolutePath)
    }

    suspend fun restoreFromVault(doc: DocumentEntity): DocumentEntity = withContext(Dispatchers.IO) {
        val vaultFile = File(doc.filePath)
        val dest = uniqueFileIn(documentsDir, "${doc.title}.pdf")
        vaultSecurityManager.decryptVaultFileTo(vaultFile, dest)
        vaultFile.delete()
        documentDao.updateVaultState(doc.id, vaulted = false, newPath = dest.absolutePath)
        doc.copy(isVaulted = false, filePath = dest.absolutePath)
    }

    suspend fun decryptVaultTempFile(doc: DocumentEntity): File = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "vault_tmp").apply { mkdirs() }
        val tmpFile = File(tmpDir, "unlocked_${doc.id}.pdf")
        vaultSecurityManager.decryptVaultFileTo(File(doc.filePath), tmpFile)
        tmpFile
    }

    suspend fun findDuplicateGroups(): List<List<DocumentEntity>> = withContext(Dispatchers.IO) {
        documentDao.getAllSnapshot()
            .filter { it.contentHash.isNotEmpty() }
            .groupBy { it.contentHash }
            .values
            .filter { it.size > 1 }
    }

    suspend fun computeStorageUsedBytes(): Long = withContext(Dispatchers.IO) {
        listOf(documentsDir, vaultDir, exportsDir, signaturesDir).sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    suspend fun clearAppCache(): Long = withContext(Dispatchers.IO) {
        var cleared = 0L
        context.cacheDir.walkTopDown().forEach {
            if (it.isFile) {
                cleared += it.length()
                it.delete()
            }
        }
        cleared
    }

    suspend fun seedStarterLibraryIfEmpty() = withContext(Dispatchers.IO) {
        if (documentDao.countAll() > 0) return@withContext

        // 1. DocuVerse Welcome Guide with TOC
        val guideFile = File(documentsDir, "DocuVerse_Quick_Start_Guide.pdf")
        createGuidePdf(guideFile)
        registerPdfFile(guideFile, "DocuVerse Quick Start Guide", DocumentType.PDF).also {
            documentDao.setStarred(it.id, true)
            documentDao.updateReadProgress(it.id, System.currentTimeMillis() - 60_000L, 0)
        }

        // 2. Research Paper & Bilingual Reference PDF
        val reportFile = File(documentsDir, "Modern_Document_Workflows_2026.pdf")
        createMultiPageReportPdf(reportFile)
        registerPdfFile(reportFile, "Modern Document Workflows 2026", DocumentType.PDF).also {
            documentDao.updateReadProgress(it.id, System.currentTimeMillis() - 3600_000L, 1)
        }

        // 3. Interactive Fillable Form PDF
        val formFile = File(documentsDir, "Client_Intake_Fillable_Form.pdf")
        createFillableFormPdf(formFile)
        registerPdfFile(formFile, "Client Intake Fillable Form", DocumentType.PDF)
    }

    private fun createGuidePdf(out: File) {
        PDDocument().use { doc ->
            val outline = PDDocumentOutline()
            doc.documentCatalog.documentOutline = outline

            val chapters = listOf(
                "1. Welcome to DocuVerse" to listOf(
                    "DocuVerse is your complete offline-first PDF Reader and Document Studio.",
                    "Browse PDFs, DOCX, XLSX, PPTX, and TXT files effortlessly.",
                    "Switch between Single Page, Continuous Vertical, Two-Page Book, and Horizontal Swipe.",
                    "Use Light, Dark, Sepia, or Mint paper themes for comfortable extended reading."
                ),
                "2. Reading Comfort, Reflow & TTS" to listOf(
                    "Enable Text Reflow to customize font size, line spacing, and alignment.",
                    "Listen hands-free with Android Text-to-Speech: play, pause, adjust speed, and switch voices.",
                    "Search keywords across every page, add bookmarks, and jump via Table of Contents."
                ),
                "3. PDF Tools, Scanner & Security" to listOf(
                    "Capture multi-page scans with edge detection, perspective crop, and Magic Color filters.",
                    "Merge, split, reorder, rotate, compress, watermark, and password-protect PDFs.",
                    "Protect sensitive files inside the AES-256 Keystore Private Vault with PIN & Biometrics."
                )
            )

            chapters.forEachIndexed { idx, (heading, lines) ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val dest = PDPageFitDestination().apply { this.page = page }
                outline.addLast(PDOutlineItem().apply {
                    title = heading
                    destination = dest
                })
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                    cs.newLineAtOffset(54f, 770f)
                    cs.showText(heading)
                    cs.endText()

                    var y = 730f
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12.5f)
                    cs.newLineAtOffset(54f, y)
                    lines.forEach { line ->
                        cs.showText(line)
                        cs.newLineAtOffset(0f, -24f)
                        y -= 24f
                    }
                    cs.endText()

                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
                    cs.newLineAtOffset(54f, 40f)
                    cs.showText("DocuVerse - Everything You Need for Documents | Page ${idx + 1}")
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    private fun createMultiPageReportPdf(out: File) {
        val text = """
Executive Summary
In 2026, mobile knowledge workers expect instant offline-first access to contracts, academic papers, spreadsheets, and scanned forms without sacrificing privacy.

Key Findings
1. Local Processing Speed: Hardware-accelerated page rendering keeps large multi-page PDFs responsive at 60 frames per second.
2. Security First: AES-256-GCM encryption via Android Keystore ensures confidential agreements never leave the device unencrypted.
3. Structured Conversion: Bidirectional OOXML parsing enables seamless movement between PDF, Word (.docx), Excel (.xlsx), and PowerPoint (.pptx).

Important Metrics & Dates
- Q1 2026 Adoption: 78% of mobile teams requested built-in OCR and digital signature workflows.
- Compression Efficiency: Medium PDF compression achieves 45% to 65% file size reduction while keeping typography sharp.
- Compliance Standard: Zero-permission Storage Access Framework (SAF) and Photo Picker safeguard user privacy.
        """.trimIndent()
        PDDocument().use { doc ->
            val p1 = PDPage(PDRectangle.A4)
            val p2 = PDPage(PDRectangle.A4)
            doc.addPage(p1)
            doc.addPage(p2)
            val lines = text.lines()
            val half = lines.size / 2
            listOf(p1 to lines.take(half), p2 to lines.drop(half)).forEachIndexed { idx, (page, chunk) ->
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    cs.newLineAtOffset(50f, 775f)
                    cs.showText("Modern Document Workflows 2026 (Part ${idx + 1})")
                    cs.endText()

                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 11.5f)
                    cs.newLineAtOffset(50f, 735f)
                    chunk.forEach { l ->
                        cs.showText(l.take(86))
                        cs.newLineAtOffset(0f, -20f)
                    }
                    cs.endText()
                }
            }
            doc.save(out)
        }
    }

    private fun createFillableFormPdf(out: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            val acroForm = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acroForm

            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                cs.newLineAtOffset(54f, 770f)
                cs.showText("DocuVerse Client Intake Form")
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(54f, 710f)
                cs.showText("Full Name:")
                cs.newLineAtOffset(0f, -55f)
                cs.showText("Email Address:")
                cs.newLineAtOffset(0f, -55f)
                cs.showText("Project Reference:")
                cs.endText()
            }

            fun addField(name: String, y: Float, defaultVal: String) {
                val field = PDTextField(acroForm).apply {
                    partialName = name
                    value = defaultVal
                }
                acroForm.fields.add(field)
                val widget: PDAnnotationWidget = field.widgets[0]
                widget.rectangle = PDRectangle(180f, y, 320f, 24f)
                widget.page = page
                page.annotations.add(widget)
            }

            addField("full_name", 704f, "Alex Rivera")
            addField("email_address", 649f, "alex@example.com")
            addField("project_reference", 594f, "DOC-2026-01")
            doc.save(out)
        }
    }

    fun uniqueFileIn(dir: File, fileName: String): File {
        val cleanName = fileName.replace(Regex("""[^a-zA-Z0-9._-]"""), "_")
        var candidate = File(dir, cleanName)
        var counter = 1
        val base = cleanName.substringBeforeLast('.')
        val ext = cleanName.substringAfterLast('.', "")
        while (candidate.exists()) {
            val suffix = "_$counter"
            candidate = File(dir, if (ext.isEmpty()) "$base$suffix" else "$base$suffix.$ext")
            counter++
        }
        return candidate
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read > 0) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
