package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.ai.SummaryMode
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DocumentEntity
import com.example.data.pdf.PdfEngine
import com.example.data.repository.DocumentRepository
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AiUiState(
    val selectedDocument: DocumentEntity? = null,
    val selectedSummaryMode: SummaryMode = SummaryMode.SHORT,
    val summaryOutput: String = "",
    val summaryCitedPages: List<Int> = emptyList(),
    val isGenerating: Boolean = false,
    val isPlaceholderMode: Boolean = true,
    val error: String? = null
)

class AiViewModel(
    private val repository: DocumentRepository,
    private val pdfEngine: PdfEngine,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiUiState(isPlaceholderMode = !aiRepository.isLiveGeminiEnabled)
    )
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedDocId = MutableStateFlow<Long?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val chatMessages: StateFlow<List<ChatMessageEntity>> = selectedDocId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeChat(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var cachedPagesText: List<String> = emptyList()

    fun selectDocument(doc: DocumentEntity) {
        if (_uiState.value.selectedDocument?.id == doc.id) return
        _uiState.update {
            it.copy(
                selectedDocument = doc,
                summaryOutput = "",
                summaryCitedPages = emptyList(),
                error = null
            )
        }
        selectedDocId.value = doc.id
        viewModelScope.launch {
            cachedPagesText = pdfEngine.extractAllPagesText(File(doc.filePath))
        }
    }

    fun generateSummary(mode: SummaryMode, language: AppLanguage) {
        val doc = _uiState.value.selectedDocument ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSummaryMode = mode, isGenerating = true, error = null) }
            runCatching {
                if (cachedPagesText.isEmpty()) {
                    cachedPagesText = pdfEngine.extractAllPagesText(File(doc.filePath))
                }
                val reply = aiRepository.generateSummary(doc.title, cachedPagesText, mode, language)
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        summaryOutput = reply.answer,
                        summaryCitedPages = reply.citedPages
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isGenerating = false, error = err.localizedMessage ?: "Summary generation failed")
                }
            }
        }
    }

    fun sendChatMessage(question: String, language: AppLanguage) {
        val trimmed = question.trim()
        val doc = _uiState.value.selectedDocument ?: return
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.insertChatMessage(
                ChatMessageEntity(documentId = doc.id, isUser = true, message = trimmed)
            )
            _uiState.update { it.copy(isGenerating = true, error = null) }
            runCatching {
                if (cachedPagesText.isEmpty()) {
                    cachedPagesText = pdfEngine.extractAllPagesText(File(doc.filePath))
                }
                val reply = aiRepository.askQuestion(doc.title, cachedPagesText, trimmed, language)
                repository.insertChatMessage(
                    ChatMessageEntity(
                        documentId = doc.id,
                        isUser = false,
                        message = reply.answer,
                        pageReferences = reply.citedPages.joinToString(",")
                    )
                )
                _uiState.update { it.copy(isGenerating = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isGenerating = false, error = err.localizedMessage ?: "Chat failed")
                }
            }
        }
    }

    fun clearChat() {
        val id = _uiState.value.selectedDocument?.id ?: return
        viewModelScope.launch { repository.clearChat(id) }
    }

    class Factory(
        private val repository: DocumentRepository,
        private val pdfEngine: PdfEngine,
        private val aiRepository: AiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AiViewModel(repository, pdfEngine, aiRepository) as T
    }
}
