package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PdfViewMode
import com.example.data.local.PreferencesRepository
import com.example.data.local.ReaderBackgroundMode
import com.example.data.local.ReflowAlignment
import com.example.data.pdf.PdfEngine
import com.example.data.pdf.PdfSearchMatch
import com.example.data.pdf.TocEntry
import com.example.data.repository.DocumentRepository
import com.example.data.tts.TtsController
import com.example.data.tts.TtsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReaderUiState(
    val document: DocumentEntity? = null,
    val activeFile: File? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val viewMode: PdfViewMode = PdfViewMode.CONTINUOUS_VERTICAL,
    val backgroundMode: ReaderBackgroundMode = ReaderBackgroundMode.LIGHT,
    val isReflowMode: Boolean = false,
    val reflowFontSizeSp: Float = 17f,
    val reflowLineSpacing: Float = 1.45f,
    val reflowAlignment: ReflowAlignment = ReflowAlignment.LEFT,
    val reflowPageText: String = "",
    val brightness: Float = -1f,
    val isFullScreen: Boolean = false,
    val isPasswordPromptRequired: Boolean = false,
    val activePassword: String = "",
    val passwordError: String? = null,
    val tocEntries: List<TocEntry> = emptyList(),
    val searchQuery: String = "",
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val activeMatchIndex: Int = 0,
    val recentlyViewedPages: List<Int> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ReaderViewModel(
    private val documentId: Long,
    private val initialPage: Int,
    private val repository: DocumentRepository,
    private val pdfEngine: PdfEngine,
    private val preferencesRepository: PreferencesRepository,
    val ttsController: TtsController
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.observeBookmarks(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ttsState: StateFlow<TtsUiState> = ttsController.state

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val prefs = preferencesRepository.preferences.first()
            val doc = repository.getDocument(documentId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, error = "Document not found.") }
                return@launch
            }
            val file = runCatching {
                if (doc.isVaulted) repository.decryptVaultTempFile(doc) else File(doc.filePath)
            }.getOrElse { err ->
                _uiState.update { it.copy(isLoading = false, error = err.localizedMessage ?: "Cannot open file.") }
                return@launch
            }

            if (!file.exists()) {
                _uiState.update { it.copy(isLoading = false, error = "PDF file missing on disk.") }
                return@launch
            }

            val needsPassword = pdfEngine.isPasswordProtected(file)
            if (needsPassword) {
                _uiState.update {
                    it.copy(
                        document = doc,
                        activeFile = file,
                        viewMode = prefs.defaultViewMode,
                        backgroundMode = prefs.defaultReaderBg,
                        reflowFontSizeSp = prefs.reflowFontSizeSp,
                        reflowLineSpacing = prefs.reflowLineSpacing,
                        reflowAlignment = prefs.reflowAlignment,
                        brightness = prefs.readerBrightness,
                        isPasswordPromptRequired = true,
                        isLoading = false
                    )
                }
                return@launch
            }

            val count = pdfEngine.getPageCount(file).coerceAtLeast(1)
            val startPage = (if (initialPage > 0) initialPage else doc.lastViewedPage).coerceIn(0, count - 1)
            val toc = pdfEngine.extractTableOfContents(file)

            _uiState.update {
                it.copy(
                    document = doc,
                    activeFile = file,
                    pageCount = count,
                    currentPage = startPage,
                    viewMode = prefs.defaultViewMode,
                    backgroundMode = prefs.defaultReaderBg,
                    reflowFontSizeSp = prefs.reflowFontSizeSp,
                    reflowLineSpacing = prefs.reflowLineSpacing,
                    reflowAlignment = prefs.reflowAlignment,
                    brightness = prefs.readerBrightness,
                    tocEntries = toc,
                    recentlyViewedPages = listOf(startPage),
                    isLoading = false
                )
            }
            repository.recordOpen(doc.id, startPage)
            loadPageTextForReflowAndTts(startPage)
        }
    }

    fun unlockWithPassword(password: String) {
        val file = _uiState.value.activeFile ?: return
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            runCatching {
                val count = pdfEngine.getPageCount(file, password).coerceAtLeast(1)
                val toc = pdfEngine.extractTableOfContents(file, password)
                val startPage = doc.lastViewedPage.coerceIn(0, count - 1)
                _uiState.update {
                    it.copy(
                        pageCount = count,
                        currentPage = startPage,
                        activePassword = password,
                        isPasswordPromptRequired = false,
                        passwordError = null,
                        tocEntries = toc,
                        recentlyViewedPages = listOf(startPage)
                    )
                }
                repository.recordOpen(doc.id, startPage)
                loadPageTextForReflowAndTts(startPage)
            }.onFailure {
                _uiState.update { it.copy(passwordError = "Incorrect PDF password.") }
            }
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int, widthPx: Int = 1080): Bitmap? {
        val state = _uiState.value
        val file = state.activeFile ?: return null
        return pdfEngine.renderPage(file, pageIndex, widthPx, state.activePassword.ifEmpty { null })
    }

    fun goToPage(pageIndex: Int) {
        val count = _uiState.value.pageCount
        if (count <= 0) return
        val target = pageIndex.coerceIn(0, count - 1)
        if (target == _uiState.value.currentPage && _uiState.value.reflowPageText.isNotEmpty()) return
        _uiState.update { state ->
            val recent = (listOf(target) + state.recentlyViewedPages.filter { it != target }).take(8)
            state.copy(currentPage = target, recentlyViewedPages = recent)
        }
        viewModelScope.launch {
            _uiState.value.document?.let { repository.recordOpen(it.id, target) }
            loadPageTextForReflowAndTts(target)
        }
    }

    private suspend fun loadPageTextForReflowAndTts(pageIndex: Int) {
        val file = _uiState.value.activeFile ?: return
        val text = pdfEngine.extractPageText(file, pageIndex, _uiState.value.activePassword)
        _uiState.update { it.copy(reflowPageText = text) }
        if (!ttsState.value.isPlaying) {
            ttsController.loadText(text)
        }
    }

    fun loadCurrentPageIntoTts() {
        ttsController.loadText(_uiState.value.reflowPageText)
    }

    fun setViewMode(mode: PdfViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setBackgroundMode(mode: ReaderBackgroundMode) {
        _uiState.update { it.copy(backgroundMode = mode) }
    }

    fun toggleReflowMode() {
        _uiState.update { it.copy(isReflowMode = !it.isReflowMode) }
    }

    fun updateReflowStyle(fontSizeSp: Float, lineSpacing: Float, alignment: ReflowAlignment) {
        _uiState.update {
            it.copy(
                reflowFontSizeSp = fontSizeSp.coerceIn(12f, 30f),
                reflowLineSpacing = lineSpacing.coerceIn(1.1f, 2.2f),
                reflowAlignment = alignment
            )
        }
    }

    fun setBrightness(value: Float) {
        _uiState.update { it.copy(brightness = value) }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun toggleBookmarkCurrentPage() {
        val state = _uiState.value
        val doc = state.document ?: return
        val page = state.currentPage
        viewModelScope.launch {
            val exists = bookmarks.value.any { it.pageNumber == page }
            if (exists) {
                repository.removeBookmarkAtPage(doc.id, page)
            } else {
                repository.addBookmark(doc.id, page, "Page ${page + 1}")
            }
        }
    }

    fun searchText(query: String) {
        val file = _uiState.value.activeFile ?: return
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val matches = pdfEngine.searchInPdf(file, query, _uiState.value.activePassword)
            _uiState.update { it.copy(searchMatches = matches, activeMatchIndex = 0) }
            matches.firstOrNull()?.let { goToPage(it.pageIndex) }
        }
    }

    fun jumpSearchMatch(delta: Int) {
        val matches = _uiState.value.searchMatches
        if (matches.isEmpty()) return
        val next = (_uiState.value.activeMatchIndex + delta + matches.size) % matches.size
        _uiState.update { it.copy(activeMatchIndex = next) }
        goToPage(matches[next].pageIndex)
    }

    override fun onCleared() {
        ttsController.stop()
        super.onCleared()
    }

    class Factory(
        private val documentId: Long,
        private val initialPage: Int,
        private val repository: DocumentRepository,
        private val pdfEngine: PdfEngine,
        private val preferencesRepository: PreferencesRepository,
        private val ttsController: TtsController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(documentId, initialPage, repository, pdfEngine, preferencesRepository, ttsController) as T
    }
}
