package com.example.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.local.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

enum class CloudProvider(
    val id: String,
    val displayName: String,
    val authEndpoint: String,
    val scope: String
) {
    GOOGLE_DRIVE(
        id = "google_drive",
        displayName = "Google Drive",
        authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
        scope = "https://www.googleapis.com/auth/drive.file"
    ),
    DROPBOX(
        id = "dropbox",
        displayName = "Dropbox",
        authEndpoint = "https://www.dropbox.com/oauth2/authorize",
        scope = "files.content.write files.content.read"
    ),
    ONEDRIVE(
        id = "onedrive",
        displayName = "Microsoft OneDrive",
        authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
        scope = "Files.ReadWrite offline_access"
    )
}

data class CloudActionResult(
    val success: Boolean,
    val message: String,
    val requiresOAuthConfig: Boolean = false
)

class CloudSyncRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val httpClient: OkHttpClient
) {
    fun buildOAuthIntent(provider: CloudProvider, clientId: String, redirectUri: String): Intent {
        val uri = Uri.parse(provider.authEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", provider.scope)
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun uploadToProvider(
        provider: CloudProvider,
        file: File,
        accessToken: String?
    ): CloudActionResult = withContext(Dispatchers.IO) {
        if (accessToken.isNullOrBlank()) {
            return@withContext CloudActionResult(
                success = false,
                requiresOAuthConfig = true,
                message = "${provider.displayName} requires configuring an OAuth Client ID and completing sign-in before uploading."
            )
        }
        val mediaType = "application/pdf".toMediaType()
        val request = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=media")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(file.asRequestBody(mediaType))
                .build()
            CloudProvider.DROPBOX -> Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Dropbox-API-Arg", """{"path":"/DocuVerse/${file.name}","mode":"overwrite"}""")
                .post(file.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            CloudProvider.ONEDRIVE -> Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/DocuVerse/${file.name}:/content")
                .addHeader("Authorization", "Bearer $accessToken")
                .put(file.asRequestBody(mediaType))
                .build()
        }
        runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    preferencesRepository.recordCloudSync()
                    CloudActionResult(true, "Uploaded ${file.name} to ${provider.displayName}.")
                } else {
                    CloudActionResult(false, "${provider.displayName} returned HTTP ${resp.code}.")
                }
            }
        }.getOrElse { err ->
            CloudActionResult(false, err.localizedMessage ?: "Cloud upload failed.")
        }
    }

    suspend fun createLocalBackupArchive(files: List<File>): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "docuverse_backup_${System.currentTimeMillis()}.zip")
        java.util.zip.ZipOutputStream(backupFile.outputStream()).use { zos ->
            files.filter { it.exists() }.forEach { f ->
                zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        preferencesRepository.recordCloudSync()
        backupFile
    }
}
