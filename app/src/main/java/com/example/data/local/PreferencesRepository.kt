package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docuverse_prefs")

enum class PdfViewMode {
    SINGLE_PAGE,
    CONTINUOUS_VERTICAL,
    TWO_PAGE_BOOK,
    HORIZONTAL_SWIPE
}

enum class ReaderBackgroundMode {
    LIGHT,
    DARK,
    SEPIA,
    MINT
}

enum class ReflowAlignment {
    LEFT, CENTER, JUSTIFY
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val defaultViewMode: PdfViewMode = PdfViewMode.CONTINUOUS_VERTICAL,
    val defaultReaderBg: ReaderBackgroundMode = ReaderBackgroundMode.LIGHT,
    val reflowFontSizeSp: Float = 17f,
    val reflowLineSpacing: Float = 1.45f,
    val reflowAlignment: ReflowAlignment = ReflowAlignment.LEFT,
    val readerBrightness: Float = -1f, // -1f = system default
    val appPinHash: String = "",
    val biometricEnabled: Boolean = false,
    val connectedDriveEmail: String = "",
    val connectedDropboxAccount: String = "",
    val connectedOneDriveAccount: String = "",
    val lastCloudSyncAt: Long = 0L
) {
    val isAppLockEnabled: Boolean get() = appPinHash.isNotEmpty()
}

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        val DEFAULT_READER_BG = stringPreferencesKey("default_reader_bg")
        val REFLOW_FONT_SIZE = floatPreferencesKey("reflow_font_size")
        val REFLOW_LINE_SPACING = floatPreferencesKey("reflow_line_spacing")
        val REFLOW_ALIGNMENT = stringPreferencesKey("reflow_alignment")
        val READER_BRIGHTNESS = floatPreferencesKey("reader_brightness")
        val APP_PIN_HASH = stringPreferencesKey("app_pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val DRIVE_EMAIL = stringPreferencesKey("drive_email")
        val DROPBOX_ACCOUNT = stringPreferencesKey("dropbox_account")
        val ONEDRIVE_ACCOUNT = stringPreferencesKey("onedrive_account")
        val LAST_CLOUD_SYNC = stringPreferencesKey("last_cloud_sync")
        val GRID_VIEW = intPreferencesKey("grid_view")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            language = AppLanguage.entries.find { it.code == prefs[Keys.LANGUAGE] } ?: AppLanguage.ENGLISH,
            defaultViewMode = runCatching {
                PdfViewMode.valueOf(prefs[Keys.DEFAULT_VIEW_MODE] ?: PdfViewMode.CONTINUOUS_VERTICAL.name)
            }.getOrDefault(PdfViewMode.CONTINUOUS_VERTICAL),
            defaultReaderBg = runCatching {
                ReaderBackgroundMode.valueOf(prefs[Keys.DEFAULT_READER_BG] ?: ReaderBackgroundMode.LIGHT.name)
            }.getOrDefault(ReaderBackgroundMode.LIGHT),
            reflowFontSizeSp = prefs[Keys.REFLOW_FONT_SIZE] ?: 17f,
            reflowLineSpacing = prefs[Keys.REFLOW_LINE_SPACING] ?: 1.45f,
            reflowAlignment = runCatching {
                ReflowAlignment.valueOf(prefs[Keys.REFLOW_ALIGNMENT] ?: ReflowAlignment.LEFT.name)
            }.getOrDefault(ReflowAlignment.LEFT),
            readerBrightness = prefs[Keys.READER_BRIGHTNESS] ?: -1f,
            appPinHash = prefs[Keys.APP_PIN_HASH] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            connectedDriveEmail = prefs[Keys.DRIVE_EMAIL] ?: "",
            connectedDropboxAccount = prefs[Keys.DROPBOX_ACCOUNT] ?: "",
            connectedOneDriveAccount = prefs[Keys.ONEDRIVE_ACCOUNT] ?: "",
            lastCloudSyncAt = prefs[Keys.LAST_CLOUD_SYNC]?.toLongOrNull() ?: 0L
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setDefaultViewMode(mode: PdfViewMode) {
        context.dataStore.edit { it[Keys.DEFAULT_VIEW_MODE] = mode.name }
    }

    suspend fun setDefaultReaderBg(mode: ReaderBackgroundMode) {
        context.dataStore.edit { it[Keys.DEFAULT_READER_BG] = mode.name }
    }

    suspend fun setReflowTypography(fontSizeSp: Float, lineSpacing: Float, alignment: ReflowAlignment) {
        context.dataStore.edit {
            it[Keys.REFLOW_FONT_SIZE] = fontSizeSp
            it[Keys.REFLOW_LINE_SPACING] = lineSpacing
            it[Keys.REFLOW_ALIGNMENT] = alignment.name
        }
    }

    suspend fun setReaderBrightness(brightness: Float) {
        context.dataStore.edit { it[Keys.READER_BRIGHTNESS] = brightness }
    }

    suspend fun setAppPinHash(pinHash: String) {
        context.dataStore.edit { it[Keys.APP_PIN_HASH] = pinHash }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setCloudAccount(provider: String, accountLabel: String) {
        context.dataStore.edit {
            when (provider) {
                "google_drive" -> it[Keys.DRIVE_EMAIL] = accountLabel
                "dropbox" -> it[Keys.DROPBOX_ACCOUNT] = accountLabel
                "onedrive" -> it[Keys.ONEDRIVE_ACCOUNT] = accountLabel
            }
            it[Keys.LAST_CLOUD_SYNC] = System.currentTimeMillis().toString()
        }
    }

    suspend fun recordCloudSync() {
        context.dataStore.edit {
            it[Keys.LAST_CLOUD_SYNC] = System.currentTimeMillis().toString()
        }
    }
}
