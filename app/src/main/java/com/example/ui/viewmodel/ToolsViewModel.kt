package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.converter.OfficeConverterEngine
import com.example.data.converter.OfficeFormat
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentType
import com.example.data.ocr.OcrEngine
import com.example.data.ocr.OcrLanguage
import com.example.data.pdf.CompressionLevel
import com.example.data.pdf.PageSizePreset
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.ScanFilterMode
import com.example.data.pdf.ScanQuad
import com.example.data.pdf.ScannerImageProcessor
import com.example.data.pdf.WatermarkPosition
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScannedPageItem(
    val id: Long = System.nanoTime(),
    val originalBitmap: Bitmap,
    val processedBitmap: Bitmap,
    val quad: ScanQuad = ScanQuad(),
    val filter: ScanFilterMode = ScanFilterMode.MAGIC_COLOR,
    val brightness: Float = 0f,
    val contrast: Float = 1f
)

data class OrganizerPageItem(
    val id: Long = System.nanoTime(),
    val originalIndex: Int,
    val rotationDelta: Int = 0,
    val thumbnail: Bitmap? = null
)

data class ToolsUiState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val lastCreatedDocument: DocumentEntity? = null,
    // Scanner
    val scannedPages: List<ScannedPageItem> = emptyList(),
    // Compression preview
    val compressionOriginalBytes: Long = 0L,
    val compressionResultBytes: Long = 0L,
    val compressedPreviewFile: File? = null,
    // Organizer
    val organizerPages: List<OrganizerPageItem> = emptyList(),
    // OCR
    val ocrExtractedText: String = "",
    val ocrNote: String? = null,
    val ocrPreviewBitmap: Bitmap? = null,
    // Office Export path
    val lastExportedOfficeFile: File? = null
)

class ToolsViewModel(
    private val repository: DocumentRepository,
    private val pdfEngine: PdfEngine,
    private val officeConverterEngine: OfficeConverterEngine,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- IMAGE TO PDF ----
    fun createPdfFromImages(
        uris: List<Uri>,
        outputTitle: String,
        pageSize: PageSizePreset,
        landscape: Boolean,
        marginPt: Float,
        onDone: (DocumentEntity) -> Unit
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = null) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${outputTitle.ifBlank { "Images_Document" }}.pdf")
                pdfEngine.imagesToPdf(uris, outFile, pageSize, landscape, marginPt)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = doc, statusMessage = "Created ${doc.title}.pdf") }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Image to PDF failed") }
            }
        }
    }

    // ---- SMART SCANNER ----
    fun addScannedBitmap(raw: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val quad = ScannerImageProcessor.detectDocumentBounds(raw)
            val cropped = ScannerImageProcessor.applyPerspectiveCrop(raw, quad)
            val enhanced = ScannerImageProcessor.applyEnhancement(cropped, ScanFilterMode.MAGIC_COLOR)
            val item = ScannedPageItem(
                originalBitmap = raw,
                processedBitmap = enhanced,
                quad = quad,
                filter = ScanFilterMode.MAGIC_COLOR
            )
            _uiState.update { it.copy(scannedPages = it.scannedPages + item) }
        }
    }

    fun updateScanPageSettings(
        index: Int,
        quad: ScanQuad,
        filter: ScanFilterMode,
        brightness: Float,
        contrast: Float
    ) {
        val current = _uiState.value.scannedPages.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val cropped = ScannerImageProcessor.applyPerspectiveCrop(current.originalBitmap, quad)
            val enhanced = ScannerImageProcessor.applyEnhancement(cropped, filter, brightness, contrast)
            _uiState.update { state ->
                val list = state.scannedPages.toMutableList()
                if (index in list.indices) {
                    list[index] = current.copy(
                        processedBitmap = enhanced,
                        quad = quad,
                        filter = filter,
                        brightness = brightness,
                        contrast = contrast
                    )
                }
                state.copy(scannedPages = list)
            }
        }
    }

    fun removeScanPage(index: Int) {
        _uiState.update { state ->
            val list = state.scannedPages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(scannedPages = list)
        }
    }

    fun exportScannedPagesToPdf(title: String, onDone: (DocumentEntity) -> Unit) {
        val pages = _uiState.value.scannedPages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${title.ifBlank { "Scanned_Doc" }}.pdf")
                pdfEngine.bitmapsToPdf(pages.map { it.processedBitmap }, outFile)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.SCANNED_PDF)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        scannedPages = emptyList(),
                        lastCreatedDocument = doc,
                        statusMessage = "Saved scanned PDF (${pages.size} pages)"
                    )
                }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Scan export failed") }
            }
        }
    }

    // ---- NOTES & WEBPAGE TO PDF ----
    fun createPdfFromText(title: String, body: String, onDone: (DocumentEntity) -> Unit) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${title.ifBlank { "Note_Document" }}.pdf")
                pdfEngine.textToPdf(title.ifBlank { "DocuVerse Note" }, body, outFile)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = doc, statusMessage = "Created \"${doc.title}\"") }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "PDF creation failed") }
            }
        }
    }

    fun saveRenderedWebBitmapsToPdf(title: String, bitmaps: List<Bitmap>, onDone: (DocumentEntity) -> Unit) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${title.ifBlank { "Webpage_Capture" }}.pdf")
                pdfEngine.bitmapsToPdf(bitmaps, outFile, PageSizePreset.A4, marginPt = 12f)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = doc, statusMessage = "Saved webpage as PDF") }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Webpage PDF failed") }
            }
        }
    }

    // ---- OFFICE CONVERTER ----
    fun convertOfficeToPdf(uri: Uri, name: String, format: OfficeFormat, onDone: (DocumentEntity) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${name.substringBeforeLast('.')}.pdf")
                officeConverterEngine.convertOfficeUriToPdf(uri, name, format, outFile)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = doc, statusMessage = "Converted ${format.ext.uppercase()} to PDF") }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Conversion failed") }
            }
        }
    }

    fun convertPdfToOffice(doc: DocumentEntity, format: OfficeFormat) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.exportsDir, "${doc.title}.${format.ext}")
                officeConverterEngine.convertPdfToOffice(File(doc.filePath), format, outFile)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastExportedOfficeFile = outFile,
                        statusMessage = "Exported ${outFile.name}"
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Export failed") }
            }
        }
    }

    // ---- MERGE / SPLIT / PAGE ORGANIZER ----
    fun mergePdfs(selectedDocs: List<DocumentEntity>, outputTitle: String, onDone: (DocumentEntity) -> Unit) {
        if (selectedDocs.size < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${outputTitle.ifBlank { "Merged_Document" }}.pdf")
                pdfEngine.mergePdfs(selectedDocs.map { File(it.filePath) }, outFile)
                val doc = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = doc, statusMessage = "Merged ${selectedDocs.size} PDFs") }
                onDone(doc)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Merge failed") }
            }
        }
    }

    fun splitPdf(doc: DocumentEntity, zeroBasedPages: List<Int>, outputTitle: String, onDone: (DocumentEntity) -> Unit) {
        if (zeroBasedPages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${outputTitle.ifBlank { "${doc.title}_Split" }}.pdf")
                pdfEngine.splitPdf(File(doc.filePath), zeroBasedPages, outFile)
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Extracted ${zeroBasedPages.size} pages") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Split failed") }
            }
        }
    }

    fun loadOrganizerPages(doc: DocumentEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val file = File(doc.filePath)
            val count = pdfEngine.getPageCount(file)
            val list = (0 until count).map { idx ->
                OrganizerPageItem(
                    originalIndex = idx,
                    thumbnail = pdfEngine.renderPage(file, idx, 260)
                )
            }
            _uiState.update { it.copy(isBusy = false, organizerPages = list) }
        }
    }

    fun moveOrganizerPage(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.organizerPages.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            state.copy(organizerPages = list)
        }
    }

    fun rotateOrganizerPage(index: Int) {
        _uiState.update { state ->
            val list = state.organizerPages.toMutableList()
            if (index in list.indices) {
                list[index] = list[index].copy(rotationDelta = (list[index].rotationDelta + 90) % 360)
            }
            state.copy(organizerPages = list)
        }
    }

    fun duplicateOrganizerPage(index: Int) {
        _uiState.update { state ->
            val list = state.organizerPages.toMutableList()
            if (index in list.indices) {
                list.add(index + 1, list[index].copy(id = System.nanoTime()))
            }
            state.copy(organizerPages = list)
        }
    }

    fun deleteOrganizerPage(index: Int) {
        _uiState.update { state ->
            if (state.organizerPages.size <= 1) return@update state
            val list = state.organizerPages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(organizerPages = list)
        }
    }

    fun saveOrganizedPdf(
        sourceDoc: DocumentEntity,
        extraInsertDoc: DocumentEntity?,
        insertAfterPos: Int,
        outputTitle: String,
        onDone: (DocumentEntity) -> Unit
    ) {
        val pages = _uiState.value.organizerPages
        if (pages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${outputTitle.ifBlank { "${sourceDoc.title}_Organized" }}.pdf")
                pdfEngine.organizePages(
                    inputFile = File(sourceDoc.filePath),
                    pageSpecs = pages.map { it.originalIndex to it.rotationDelta },
                    extraInsertFile = extraInsertDoc?.let { File(it.filePath) },
                    insertAfterPos = insertAfterPos,
                    outputFile = outFile
                )
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Saved organized PDF") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Organize failed") }
            }
        }
    }

    // ---- COMPRESSION ----
    fun previewCompression(doc: DocumentEntity, level: CompressionLevel) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val src = File(doc.filePath)
                val tmp = File(repository.exportsDir, "preview_compressed_${doc.id}.pdf")
                pdfEngine.compressPdf(src, tmp, level)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        compressionOriginalBytes = src.length(),
                        compressionResultBytes = tmp.length(),
                        compressedPreviewFile = tmp
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Compression failed") }
            }
        }
    }

    fun saveCompressedPreview(sourceDoc: DocumentEntity, onDone: (DocumentEntity) -> Unit) {
        val preview = _uiState.value.compressedPreviewFile ?: return
        if (!preview.exists()) return
        viewModelScope.launch {
            val dest = repository.uniqueFileIn(repository.documentsDir, "${sourceDoc.title}_Compressed.pdf")
            withContext(Dispatchers.IO) { preview.copyTo(dest, overwrite = true) }
            val saved = repository.registerPdfFile(dest, dest.nameWithoutExtension, DocumentType.PDF)
            _uiState.update { it.copy(lastCreatedDocument = saved, compressedPreviewFile = null, statusMessage = "Saved compressed PDF") }
            onDone(saved)
        }
    }

    // ---- PROTECT / UNLOCK / WATERMARK ----
    fun protectPdfWithPassword(doc: DocumentEntity, password: String, onDone: (DocumentEntity) -> Unit) {
        if (password.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${doc.title}_Protected.pdf")
                pdfEngine.protectPdf(File(doc.filePath), outFile, password)
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Password protection applied") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Encryption failed") }
            }
        }
    }

    fun applyTextWatermark(
        doc: DocumentEntity,
        text: String,
        opacity: Float,
        position: WatermarkPosition,
        onDone: (DocumentEntity) -> Unit
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${doc.title}_Watermarked.pdf")
                pdfEngine.addTextWatermark(File(doc.filePath), outFile, text, opacity, position)
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Watermark applied") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Watermark failed") }
            }
        }
    }

    fun applyImageWatermark(
        doc: DocumentEntity,
        imageUri: Uri,
        opacity: Float,
        position: WatermarkPosition,
        onDone: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${doc.title}_LogoWatermark.pdf")
                pdfEngine.addImageWatermark(File(doc.filePath), imageUri, outFile, opacity, position)
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Image watermark applied") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Image watermark failed") }
            }
        }
    }

    // ---- OCR & SEARCHABLE PDF ----
    fun runOcrOnImageUri(uri: Uri, language: OcrLanguage) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val bmp = withContext(Dispatchers.IO) { pdfEngine.decodeSampledBitmap(uri, 1800, 2400) }
                    ?: error("Unable to load image")
                val res = ocrEngine.recognizeBitmap(bmp, language)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        ocrExtractedText = res.extractedText,
                        ocrNote = res.note,
                        ocrPreviewBitmap = bmp
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "OCR failed") }
            }
        }
    }

    fun runOcrOnPdfPage(doc: DocumentEntity, pageIndex: Int, language: OcrLanguage) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val bmp = pdfEngine.renderPage(File(doc.filePath), pageIndex, 1600)
                    ?: error("Unable to render PDF page")
                val res = ocrEngine.recognizeBitmap(bmp, language)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        ocrExtractedText = res.extractedText,
                        ocrNote = res.note,
                        ocrPreviewBitmap = bmp
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "PDF OCR failed") }
            }
        }
    }

    fun updateOcrCorrectedText(text: String) {
        _uiState.update { it.copy(ocrExtractedText = text) }
    }

    fun saveSearchableOcrPdf(title: String, onDone: (DocumentEntity) -> Unit) {
        val bmp = _uiState.value.ocrPreviewBitmap ?: return
        val text = _uiState.value.ocrExtractedText
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${title.ifBlank { "OCR_Searchable" }}.pdf")
                ocrEngine.createSearchablePdfFromImages(listOf(bmp to text), outFile)
                val saved = repository.registerPdfFile(outFile, outFile.nameWithoutExtension, DocumentType.SCANNED_PDF)
                _uiState.update { it.copy(isBusy = false, lastCreatedDocument = saved, statusMessage = "Saved searchable PDF") }
                onDone(saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, statusMessage = e.localizedMessage ?: "Save searchable PDF failed") }
            }
        }
    }

    fun exportOcrAsTxt(title: String) {
        val text = _uiState.value.ocrExtractedText
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val outFile = repository.uniqueFileIn(repository.exportsDir, "${title.ifBlank { "OCR_Extract" }}.txt")
            outFile.writeText(text)
            _uiState.update { it.copy(lastExportedOfficeFile = outFile, statusMessage = "Saved ${outFile.name}") }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val repository: DocumentRepository,
        private val pdfEngine: PdfEngine,
        private val officeConverterEngine: OfficeConverterEngine,
        private val ocrEngine: OcrEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ToolsViewModel(repository, pdfEngine, officeConverterEngine, ocrEngine) as T
    }
}
