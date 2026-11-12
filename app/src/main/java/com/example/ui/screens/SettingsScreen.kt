package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudProvider
import com.example.data.local.PdfViewMode
import com.example.data.local.ReaderBackgroundMode
import com.example.data.local.ReflowAlignment
import com.example.data.local.UserPreferences
import com.example.ui.components.formatFileSize
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsUiState

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    uiState: SettingsUiState,
    onSetTheme: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetDefaultViewMode: (PdfViewMode) -> Unit,
    onSetDefaultReaderBg: (ReaderBackgroundMode) -> Unit,
    onSetReflowDefaults: (Float, Float, ReflowAlignment) -> Unit,
    onSetAppPin: (String) -> Unit,
    onSetBiometric: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onConnectCloud: (CloudProvider, String) -> Unit,
    onDisconnectCloud: (CloudProvider) -> Unit,
    onCreateLocalBackup: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var cloudPermissionProvider by remember { mutableStateOf<CloudProvider?>(null) }
    var oauthClientIdInput by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = strings.settingsTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)
            )
        }

        // Appearance & Theme
        item {
            SettingsSectionCard(title = strings.settingsTheme, icon = Icons.Filled.Palette) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { onSetTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Language Selection (English & Bengali)
        item {
            SettingsSectionCard(title = strings.settingsLanguage, icon = Icons.Filled.Language) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = prefs.language == lang,
                            onClick = { onSetLanguage(lang) },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("lang_${lang.code}")
                        )
                    }
                }
            }
        }

        // Reading & Font Preferences
        item {
            SettingsSectionCard(title = strings.settingsReadingPrefs, icon = Icons.Filled.Info) {
                Text("Default Viewing Mode", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PdfViewMode.CONTINUOUS_VERTICAL to "Scroll",
                        PdfViewMode.SINGLE_PAGE to "Single",
                        PdfViewMode.TWO_PAGE_BOOK to "Book",
                        PdfViewMode.HORIZONTAL_SWIPE to "Swipe"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.defaultViewMode == mode,
                            onClick = { onSetDefaultViewMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Default Page Background", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReaderBackgroundMode.entries.forEach { bg ->
                        FilterChip(
                            selected = prefs.defaultReaderBg == bg,
                            onClick = { onSetDefaultReaderBg(bg) },
                            label = { Text(bg.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Reflow Font Size: ${prefs.reflowFontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = prefs.reflowFontSizeSp,
                    onValueChange = { onSetReflowDefaults(it, prefs.reflowLineSpacing, prefs.reflowAlignment) },
                    valueRange = 13f..28f
                )
            }
        }

        // Security & Privacy
        item {
            SettingsSectionCard(title = strings.settingsSecurity, icon = Icons.Filled.Lock) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App PIN Lock & Vault Passcode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (prefs.isAppLockEnabled) "Enabled (Salted SHA-256)" else "Not configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { pinInput = ""; showPinDialog = true },
                        modifier = Modifier.testTag("settings_pin_btn")
                    ) {
                        Text(if (prefs.isAppLockEnabled) "Change" else "Set PIN")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Biometric Unlock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (uiState.isBiometricSupported) "Fingerprint / Face unlock" else "Not enrolled on device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = prefs.biometricEnabled,
                        onCheckedChange = { onSetBiometric(it) },
                        enabled = uiState.isBiometricSupported && prefs.isAppLockEnabled,
                        modifier = Modifier.testTag("settings_biometric_switch")
                    )
                }
            }
        }

        // Cloud Accounts & Backup
        item {
            SettingsSectionCard(title = strings.settingsCloud, icon = Icons.Filled.CloudUpload) {
                Text(
                    "Connect external cloud storage with explicit OAuth2 permission to upload, sync, and restore documents.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                CloudProvider.entries.forEach { provider ->
                    val connectedAccount = when (provider) {
                        CloudProvider.GOOGLE_DRIVE -> prefs.connectedDriveEmail
                        CloudProvider.DROPBOX -> prefs.connectedDropboxAccount
                        CloudProvider.ONEDRIVE -> prefs.connectedOneDriveAccount
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(provider.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                connectedAccount.ifBlank { "Disconnected • OAuth2 PKCE" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (connectedAccount.isBlank()) {
                            OutlinedButton(onClick = {
                                oauthClientIdInput = ""
                                cloudPermissionProvider = provider
                            }) {
                                Text("Connect")
                            }
                        } else {
                            TextButton(onClick = { onDisconnectCloud(provider) }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onCreateLocalBackup,
                    modifier = Modifier.fillMaxWidth().testTag("create_local_backup_btn")
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Local ZIP Backup Archive")
                }
            }
        }

        // Storage Management
        item {
            SettingsSectionCard(title = strings.settingsStorage, icon = Icons.Filled.Storage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Documents & Vault Used", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatFileSize(uiState.storageUsedBytes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onClearCache,
                        modifier = Modifier.testTag("clear_cache_btn")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Cache")
                    }
                }
            }
        }

        // About & Help
        item {
            SettingsSectionCard(title = strings.settingsAbout, icon = Icons.Filled.HelpOutline) {
                Text("DocuVerse v1.0", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    strings.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showHelpDialog = true }) {
                    Text("API & Configuration Guide")
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configure App & Vault PIN") },
            text = {
                Column {
                    Text("Enter a 4–8 digit PIN, or leave blank to disable lock.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8) pinInput = it },
                        label = { Text("PIN (4-8 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSetAppPin(pinInput)
                    showPinDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    cloudPermissionProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { cloudPermissionProvider = null },
            title = { Text("Connect ${provider.displayName}") },
            text = {
                Column {
                    Text(
                        "Permission notice: DocuVerse never uploads your personal documents without your explicit action. Connecting ${provider.displayName} requests scope '${provider.scope}'.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = oauthClientIdInput,
                        onValueChange = { oauthClientIdInput = it },
                        label = { Text("${provider.displayName} OAuth Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onConnectCloud(provider, oauthClientIdInput)
                    cloudPermissionProvider = null
                }) { Text("Authorize") }
            },
            dismissButton = {
                TextButton(onClick = { cloudPermissionProvider = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("DocuVerse Setup & Privacy") },
            text = {
                Text(
                    "• Offline Reading, Editing, Merge/Split, Compression, Watermarks, and AES-256 Vault work 100% locally.\n" +
                        "• Live Gemini AI Summary & Chat require enabling Firebase AI Logic.\n" +
                        "• Cloud Drive/Dropbox/OneDrive sync uses official OAuth2 PKCE with your own Client ID.\n" +
                        "• No broad storage permissions are requested; all imports use Android SAF & Photo Picker.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
