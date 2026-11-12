package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AnnotationEntity
import com.example.data.local.AnnotationToolType
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentType
import com.example.data.local.SignatureEntity
import com.example.data.pdf.AcroFormFieldInfo
import com.example.data.pdf.NormalizedPoint
import com.example.data.pdf.PdfEditorEngine
import com.example.data.pdf.PdfEngine
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class EditorUiState(
    val document: DocumentEntity? = null,
    val activeFile: File? = null,
    val pageCount: Int = 1,
    val currentPage: Int = 0,
    val activeTool: AnnotationToolType = AnnotationToolType.HIGHLIGHT,
    val isEraserActive: Boolean = false,
    val selectedColorArgb: Long = 0xFFFACC15L,
    val strokeWidth: Float = 6f,
    val formFields: List<AcroFormFieldInfo> = emptyList(),
    val formValues: Map<String, String> = emptyMap(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

class EditorViewModel(
    private val documentId: Long,
    private val initialPage: Int,
    private val repository: DocumentRepository,
    private val pdfEngine: PdfEngine,
    private val pdfEditorEngine: PdfEditorEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    val annotations: StateFlow<List<AnnotationEntity>> = repository.observeAnnotations(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedSignatures: StateFlow<List<SignatureEntity>> = repository.savedSignatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val undoStack = mutableListOf<Long>() // inserted annotation IDs
    private val redoStack = mutableListOf<AnnotationEntity>()

    init {
        viewModelScope.launch {
            val doc = repository.getDocument(documentId) ?: return@launch
            val file = if (doc.isVaulted) repository.decryptVaultTempFile(doc) else File(doc.filePath)
            val count = pdfEngine.getPageCount(file).coerceAtLeast(1)
            val fields = pdfEngine.extractAcroFormFields(file)
            _uiState.update {
                it.copy(
                    document = doc,
                    activeFile = file,
                    pageCount = count,
                    currentPage = initialPage.coerceIn(0, count - 1),
                    formFields = fields,
                    formValues = fields.associate { f -> f.fullyQualifiedName to f.currentValue }
                )
            }
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int, widthPx: Int = 1080): Bitmap? {
        val file = _uiState.value.activeFile ?: return null
        return pdfEngine.renderPage(file, pageIndex, widthPx)
    }

    fun selectTool(tool: AnnotationToolType) {
        _uiState.update { it.copy(activeTool = tool, isEraserActive = false) }
    }

    fun toggleEraser() {
        _uiState.update { it.copy(isEraserActive = !it.isEraserActive) }
    }

    fun selectColor(argb: Long) {
        _uiState.update { it.copy(selectedColorArgb = argb) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(strokeWidth = width) }
    }

    fun goToPage(page: Int) {
        val count = _uiState.value.pageCount
        _uiState.update { it.copy(currentPage = page.coerceIn(0, count - 1)) }
    }

    fun addStrokeAnnotation(points: List<NormalizedPoint>) {
        if (points.size < 2) return
        val state = _uiState.value
        val doc = state.document ?: return
        viewModelScope.launch {
            val entity = AnnotationEntity(
                documentId = doc.id,
                pageNumber = state.currentPage,
                toolType = state.activeTool.name,
                colorArgb = state.selectedColorArgb,
                strokeWidth = state.strokeWidth,
                pointsJson = Json.encodeToString(points)
            )
            val id = repository.addAnnotation(entity)
            undoStack.add(id)
            redoStack.clear()
            _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
        }
    }

    fun addPositionedAnnotation(
        tool: AnnotationToolType,
        xNorm: Float,
        yNorm: Float,
        textContent: String = "",
        extraPath: String = "",
        widthNorm: Float = 0.28f,
        heightNorm: Float = 0.1f
    ) {
        val state = _uiState.value
        val doc = state.document ?: return
        viewModelScope.launch {
            val entity = AnnotationEntity(
                documentId = doc.id,
                pageNumber = state.currentPage,
                toolType = tool.name,
                colorArgb = state.selectedColorArgb,
                strokeWidth = state.strokeWidth,
                xNorm = xNorm.coerceIn(0.02f, 0.85f),
                yNorm = yNorm.coerceIn(0.02f, 0.9f),
                widthNorm = widthNorm,
                heightNorm = heightNorm,
                textContent = textContent,
                extraPath = extraPath
            )
            val id = repository.addAnnotation(entity)
            undoStack.add(id)
            redoStack.clear()
            _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
        }
    }

    fun eraseNearestAnnotation(xNorm: Float, yNorm: Float) {
        val currentPage = _uiState.value.currentPage
        val pageAnns = annotations.value.filter { it.pageNumber == currentPage }
        val target = pageAnns.lastOrNull { ann ->
            val pts = runCatching { Json.decodeFromString<List<NormalizedPoint>>(ann.pointsJson) }.getOrDefault(emptyList())
            if (pts.isNotEmpty()) {
                pts.any { kotlin.math.hypot(it.x - xNorm, it.y - yNorm) < 0.06f }
            } else {
                kotlin.math.hypot(ann.xNorm - xNorm, ann.yNorm - yNorm) < 0.09f
            }
        }
        if (target != null) {
            viewModelScope.launch {
                repository.deleteAnnotation(target.id)
            }
        }
    }

    fun insertImageUri(uri: Uri, xNorm: Float = 0.25f, yNorm: Float = 0.3f) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                val bmp = pdfEngine.decodeSampledBitmap(uri, 800, 800) ?: return@withContext ""
                val outFile = File(repository.signaturesDir, "img_ann_${System.currentTimeMillis()}.png")
                outFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                bmp.recycle()
                outFile.absolutePath
            }
            if (path.isNotEmpty()) {
                addPositionedAnnotation(
                    tool = AnnotationToolType.IMAGE,
                    xNorm = xNorm,
                    yNorm = yNorm,
                    extraPath = path,
                    widthNorm = 0.34f,
                    heightNorm = 0.18f
                )
            }
        }
    }

    fun saveAndPlaceSignature(name: String, strokes: List<List<NormalizedPoint>>) {
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            val sigFile = withContext(Dispatchers.IO) {
                val bmp = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(15, 23, 42)
                    strokeWidth = 7f
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                strokes.forEach { stroke ->
                    if (stroke.size >= 2) {
                        val path = android.graphics.Path()
                        path.moveTo(stroke.first().x * 600f, stroke.first().y * 240f)
                        for (i in 1 until stroke.size) {
                            path.lineTo(stroke[i].x * 600f, stroke[i].y * 240f)
                        }
                        canvas.drawPath(path, paint)
                    }
                }
                val file = File(repository.signaturesDir, "sig_${System.currentTimeMillis()}.png")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                file
            }
            val sig = SignatureEntity(
                name = name.ifBlank { "Signature" },
                strokesJson = Json.encodeToString(strokes),
                imagePath = sigFile.absolutePath
            )
            repository.saveSignature(sig)
            addPositionedAnnotation(
                tool = AnnotationToolType.SIGNATURE,
                xNorm = 0.3f,
                yNorm = 0.65f,
                extraPath = sigFile.absolutePath,
                widthNorm = 0.35f,
                heightNorm = 0.12f
            )
        }
    }

    fun updateFormField(fieldName: String, value: String) {
        _uiState.update {
            it.copy(formValues = it.formValues + (fieldName to value))
        }
    }

    fun undo() {
        val lastId = undoStack.removeLastOrNull() ?: return
        val existing = annotations.value.find { it.id == lastId }
        viewModelScope.launch {
            if (existing != null) redoStack.add(existing)
            repository.deleteAnnotation(lastId)
            _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        }
    }

    fun redo() {
        val entity = redoStack.removeLastOrNull() ?: return
        viewModelScope.launch {
            val newId = repository.addAnnotation(entity.copy(id = 0))
            undoStack.add(newId)
            _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        }
    }

    fun exportCompletedPdf(onExported: (DocumentEntity) -> Unit) {
        val state = _uiState.value
        val doc = state.document ?: return
        val src = state.activeFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val outFile = repository.uniqueFileIn(repository.documentsDir, "${doc.title}_Annotated.pdf")
                val allAnns = repository.getAnnotationsSnapshot(doc.id)
                pdfEditorEngine.exportAnnotatedAndFilledPdf(
                    sourceFile = src,
                    outputFile = outFile,
                    annotations = allAnns,
                    formUpdates = state.formValues
                )
                val saved = repository.registerPdfFile(
                    file = outFile,
                    title = "${doc.title} (Edited)",
                    docType = DocumentType.PDF
                )
                _uiState.update { it.copy(isSaving = false, statusMessage = "Saved \"${saved.title}\"") }
                onExported(saved)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isSaving = false, statusMessage = err.localizedMessage ?: "Export failed")
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val documentId: Long,
        private val initialPage: Int,
        private val repository: DocumentRepository,
        private val pdfEngine: PdfEngine,
        private val pdfEditorEngine: PdfEditorEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditorViewModel(documentId, initialPage, repository, pdfEngine, pdfEditorEngine) as T
    }
}
