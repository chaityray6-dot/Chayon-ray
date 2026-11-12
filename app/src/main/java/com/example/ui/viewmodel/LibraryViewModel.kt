package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.repository.DocumentContentSearchResult
import com.example.data.repository.DocumentRepository
import com.example.data.security.VaultSecurityManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SortOption(val label: String) {
    DATE_DESC("Newest"),
    NAME_ASC("Name A-Z"),
    SIZE_DESC("Largest"),
    TYPE("Type")
}

enum class FileFilterOption(val label: String) {
    ALL("All"),
    PDF("PDF"),
    OFFICE("Office"),
    SCANNED("Scanned"),
    STARRED("Starred"),
    VAULT("Private Vault")
}

data class LibraryUiState(
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_DESC,
    val filterOption: FileFilterOption = FileFilterOption.ALL,
    val isGridView: Boolean = false,
    val storageUsedBytes: Long = 0L,
    val duplicateGroupsCount: Int = 0,
    val isVaultUnlocked: Boolean = false,
    val isAppUnlocked: Boolean = false,
    val isImporting: Boolean = false,
    val isSearchingContent: Boolean = false,
    val deepSearchQuery: String = "",
    val contentSearchResults: List<DocumentContentSearchResult> = emptyList(),
    val recentSearchQueries: List<String> = emptyList(),
    val statusMessage: String? = null
)

class LibraryViewModel(
    private val context: Context,
    private val repository: DocumentRepository,
    private val preferencesRepository: PreferencesRepository,
    private val vaultSecurityManager: VaultSecurityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    private var deepSearchJob: Job? = null

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultedDocuments: StateFlow<List<DocumentEntity>> = repository.vaultedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments: StateFlow<List<DocumentEntity>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredDocuments: StateFlow<List<DocumentEntity>> = repository.starredDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        vaultedDocuments,
        _uiState
    ) { docs, vaulted, state ->
        val base = if (state.filterOption == FileFilterOption.VAULT) {
            if (state.isVaultUnlocked) vaulted else emptyList()
        } else {
            docs.filter { doc ->
                when (state.filterOption) {
                    FileFilterOption.ALL -> true
                    FileFilterOption.PDF -> doc.docType == "PDF"
                    FileFilterOption.OFFICE -> doc.docType in listOf("DOCX", "XLSX", "PPTX", "TXT")
                    FileFilterOption.SCANNED -> doc.docType == "SCANNED_PDF"
                    FileFilterOption.STARRED -> doc.isStarred
                    FileFilterOption.VAULT -> false
                }
            }
        }
        val searched = if (state.searchQuery.isBlank()) base else {
            val q = state.searchQuery.trim()
            base.filter { it.title.contains(q, ignoreCase = true) || it.docType.contains(q, ignoreCase = true) }
        }
        when (state.sortOption) {
            SortOption.DATE_DESC -> searched.sortedByDescending { it.modifiedAt }
            SortOption.NAME_ASC -> searched.sortedBy { it.title.lowercase() }
            SortOption.SIZE_DESC -> searched.sortedByDescending { it.fileSizeBytes }
            SortOption.TYPE -> searched.sortedBy { it.docType }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.seedStarterLibraryIfEmpty()
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val used = repository.computeStorageUsedBytes()
            val dupGroups = repository.findDuplicateGroups()
            _uiState.update { it.copy(storageUsedBytes = used, duplicateGroupsCount = dupGroups.size) }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun setFilterOption(option: FileFilterOption) {
        _uiState.update { it.copy(filterOption = option) }
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun unlockApp() {
        _uiState.update { it.copy(isAppUnlocked = true) }
    }

    fun unlockVault() {
        _uiState.update { it.copy(isVaultUnlocked = true) }
    }

    fun lockVault() {
        _uiState.update { it.copy(isVaultUnlocked = false) }
    }

    fun verifyPin(pin: String): Boolean {
        val stored = userPreferences.value.appPinHash
        if (stored.isEmpty()) return true
        return vaultSecurityManager.verifyPin(pin, stored)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun importUri(uri: Uri, onImported: (DocumentEntity) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching {
                val doc = repository.importDocumentFromUri(uri)
                refreshStorageStats()
                _uiState.update { it.copy(isImporting = false, statusMessage = "Imported \"${doc.title}\"") }
                onImported(doc)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isImporting = false, statusMessage = err.localizedMessage ?: "Import failed")
                }
            }
        }
    }

    fun importFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching {
                val result = repository.importDocumentsFromFolderTree(treeUri)
                refreshStorageStats()
                val msg = when {
                    result.importedCount > 0 && result.failedCount > 0 ->
                        "Imported ${result.importedCount} document(s) (${result.failedCount} skipped)"
                    result.importedCount > 0 ->
                        "Imported ${result.importedCount} document(s) from folder"
                    else -> "No new supported documents found in folder"
                }
                _uiState.update { it.copy(isImporting = false, statusMessage = msg) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isImporting = false, statusMessage = err.localizedMessage ?: "Folder import failed")
                }
            }
        }
    }

    fun toggleStar(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleStar(doc.id, !doc.isStarred)
        }
    }

    fun setStarredForDocuments(docs: List<DocumentEntity>, starred: Boolean) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            docs.forEach { repository.toggleStar(it.id, starred) }
            _uiState.update {
                it.copy(statusMessage = "${if (starred) "Starred" else "Unstarred"} ${docs.size} document(s)")
            }
        }
    }

    fun renameDocument(doc: DocumentEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameDocument(doc.id, newTitle)
            _uiState.update { it.copy(statusMessage = "Renamed to \"$newTitle\"") }
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            refreshStorageStats()
            _uiState.update { it.copy(statusMessage = "Deleted \"${doc.title}\"") }
        }
    }

    fun deleteDocuments(docs: List<DocumentEntity>) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            docs.forEach { repository.deleteDocument(it) }
            refreshStorageStats()
            _uiState.update { it.copy(statusMessage = "Deleted ${docs.size} document(s)") }
        }
    }

    fun mergeSelectedDocuments(docs: List<DocumentEntity>, onMerged: (DocumentEntity) -> Unit = {}) {
        if (docs.size < 2) {
            _uiState.update { it.copy(statusMessage = "Select at least 2 PDFs to merge") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching {
                val merged = repository.mergeDocuments(docs, "Merged_${System.currentTimeMillis() % 100000}")
                refreshStorageStats()
                _uiState.update { it.copy(isImporting = false, statusMessage = "Merged into \"${merged.title}\"") }
                onMerged(merged)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isImporting = false, statusMessage = err.localizedMessage ?: "Merge failed")
                }
            }
        }
    }

    fun moveToVault(doc: DocumentEntity) {
        viewModelScope.launch {
            runCatching {
                repository.moveToVault(doc)
                _uiState.update { it.copy(statusMessage = "Encrypted \"${doc.title}\" into Private Vault") }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Vault encryption failed") }
            }
        }
    }

    fun restoreFromVault(doc: DocumentEntity) {
        viewModelScope.launch {
            runCatching {
                repository.restoreFromVault(doc)
                _uiState.update { it.copy(statusMessage = "Restored \"${doc.title}\" from Private Vault") }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Restore failed") }
            }
        }
    }

    fun removeDuplicatesKeepNewest() {
        viewModelScope.launch {
            val groups = repository.findDuplicateGroups()
            var removed = 0
            groups.forEach { group ->
                val sorted = group.sortedByDescending { it.modifiedAt }
                sorted.drop(1).forEach { dup ->
                    repository.deleteDocument(dup)
                    removed++
                }
            }
            refreshStorageStats()
            _uiState.update { it.copy(statusMessage = "Removed $removed duplicate file(s)") }
        }
    }

    fun shareDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            runCatching {
                val file = if (doc.isVaulted) repository.decryptVaultTempFile(doc) else File(doc.filePath)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${doc.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Unable to share") }
            }
        }
    }

    fun shareDocuments(docs: List<DocumentEntity>) {
        if (docs.isEmpty()) return
        if (docs.size == 1) {
            shareDocument(docs.first())
            return
        }
        viewModelScope.launch {
            runCatching {
                val uris = ArrayList<Uri>()
                docs.forEach { doc ->
                    val file = if (doc.isVaulted) repository.decryptVaultTempFile(doc) else File(doc.filePath)
                    if (file.exists()) {
                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                    }
                }
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/pdf"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${uris.size} documents").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Unable to share selected files") }
            }
        }
    }

    fun runDeepDocumentSearch(query: String, immediate: Boolean = false) {
        _uiState.update { it.copy(deepSearchQuery = query) }
        deepSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(isSearchingContent = false, contentSearchResults = emptyList()) }
            return
        }
        deepSearchJob = viewModelScope.launch {
            if (!immediate) delay(280)
            _uiState.update { it.copy(isSearchingContent = true) }
            val results = runCatching { repository.searchAcrossDocuments(trimmed) }.getOrDefault(emptyList())
            _uiState.update { state ->
                val updatedHistory = if (trimmed.length >= 2) {
                    (listOf(trimmed) + state.recentSearchQueries.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
                } else state.recentSearchQueries
                state.copy(
                    isSearchingContent = false,
                    contentSearchResults = results,
                    recentSearchQueries = updatedHistory
                )
            }
        }
    }

    fun clearDeepSearch() {
        deepSearchJob?.cancel()
        _uiState.update {
            it.copy(deepSearchQuery = "", isSearchingContent = false, contentSearchResults = emptyList())
        }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearchQueries = emptyList()) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val context: Context,
        private val repository: DocumentRepository,
        private val preferencesRepository: PreferencesRepository,
        private val vaultSecurityManager: VaultSecurityManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(context, repository, preferencesRepository, vaultSecurityManager) as T
    }
}
