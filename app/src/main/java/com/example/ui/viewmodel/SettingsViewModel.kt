package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudProvider
import com.example.data.cloud.CloudSyncRepository
import com.example.data.local.PdfViewMode
import com.example.data.local.PreferencesRepository
import com.example.data.local.ReaderBackgroundMode
import com.example.data.local.ReflowAlignment
import com.example.data.local.UserPreferences
import com.example.data.repository.DocumentRepository
import com.example.data.security.VaultSecurityManager
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val storageUsedBytes: Long = 0L,
    val isBiometricSupported: Boolean = false,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val documentRepository: DocumentRepository,
    private val vaultSecurityManager: VaultSecurityManager,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _uiState = MutableStateFlow(
        SettingsUiState(isBiometricSupported = vaultSecurityManager.isBiometricAvailable())
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val used = documentRepository.computeStorageUsedBytes()
            _uiState.update { it.copy(storageUsedBytes = used) }
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { preferencesRepository.setLanguage(lang) }
    fun setDefaultViewMode(mode: PdfViewMode) = viewModelScope.launch { preferencesRepository.setDefaultViewMode(mode) }
    fun setDefaultReaderBg(bg: ReaderBackgroundMode) = viewModelScope.launch { preferencesRepository.setDefaultReaderBg(bg) }
    fun setReflowDefaults(fontSize: Float, spacing: Float, align: ReflowAlignment) = viewModelScope.launch {
        preferencesRepository.setReflowTypography(fontSize, spacing, align)
    }

    fun setAppPin(pin: String) {
        viewModelScope.launch {
            if (pin.isBlank()) {
                preferencesRepository.setAppPinHash("")
                preferencesRepository.setBiometricEnabled(false)
                _uiState.update { it.copy(statusMessage = "App PIN lock disabled") }
            } else {
                preferencesRepository.setAppPinHash(vaultSecurityManager.hashPin(pin))
                _uiState.update { it.copy(statusMessage = "App PIN lock updated") }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val cleared = documentRepository.clearAppCache()
            refreshStorage()
            val kb = cleared / 1024
            _uiState.update { it.copy(statusMessage = "Cleared ${kb} KB of cached renders") }
        }
    }

    fun launchCloudOAuth(provider: CloudProvider, clientId: String) {
        if (clientId.isBlank()) {
            _uiState.update {
                it.copy(
                    statusMessage = "${provider.displayName}: Enter your OAuth Client ID (see API Configuration section) before connecting."
                )
            }
            return
        }
        runCatching {
            val redirectUri = "${context.packageName}://oauth2redirect"
            val intent = cloudSyncRepository.buildOAuthIntent(provider, clientId.trim(), redirectUri)
            context.startActivity(intent)
        }.onFailure { err ->
            _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Unable to launch OAuth browser") }
        }
    }

    fun disconnectCloud(provider: CloudProvider) {
        viewModelScope.launch {
            preferencesRepository.setCloudAccount(provider.id, "")
            _uiState.update { it.copy(statusMessage = "Disconnected ${provider.displayName}") }
        }
    }

    fun createLocalBackupZip() {
        viewModelScope.launch {
            runCatching {
                val docs = documentRepository.allDocuments.first()
                val backup = cloudSyncRepository.createLocalBackupArchive(docs.map { File(it.filePath) })
                _uiState.update { it.copy(statusMessage = "Created encrypted-ready backup archive: ${backup.name}") }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = err.localizedMessage ?: "Backup failed") }
            }
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    class Factory(
        private val context: Context,
        private val preferencesRepository: PreferencesRepository,
        private val documentRepository: DocumentRepository,
        private val vaultSecurityManager: VaultSecurityManager,
        private val cloudSyncRepository: CloudSyncRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(context, preferencesRepository, documentRepository, vaultSecurityManager, cloudSyncRepository) as T
    }
}
