package com.example.di

import android.content.Context
import com.example.data.ai.AiRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.cloud.CloudSyncRepository
import com.example.data.converter.OfficeConverterEngine
import com.example.data.local.DocuVerseDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.ocr.OcrEngine
import com.example.data.pdf.PdfEditorEngine
import com.example.data.pdf.PdfEngine
import com.example.data.repository.DocumentRepository
import com.example.data.security.VaultSecurityManager
import com.example.data.tts.TtsController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: DocuVerseDatabase by lazy { DocuVerseDatabase.getInstance(appContext) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
    val vaultSecurityManager: VaultSecurityManager by lazy { VaultSecurityManager(appContext) }
    val pdfEngine: PdfEngine by lazy { PdfEngine(appContext) }
    val pdfEditorEngine: PdfEditorEngine by lazy { PdfEditorEngine(pdfEngine) }
    val officeConverterEngine: OfficeConverterEngine by lazy { OfficeConverterEngine(appContext, pdfEngine) }
    val ocrEngine: OcrEngine by lazy { OcrEngine(pdfEngine) }
    val aiRepository: AiRepository by lazy { PlaceholderAiRepository() }
    val ttsController: TtsController by lazy { TtsController(appContext) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val cloudSyncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(appContext, preferencesRepository, httpClient)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            context = appContext,
            documentDao = database.documentDao(),
            bookmarkDao = database.bookmarkDao(),
            annotationDao = database.annotationDao(),
            signatureDao = database.signatureDao(),
            chatDao = database.chatDao(),
            pdfEngine = pdfEngine,
            officeConverterEngine = officeConverterEngine,
            vaultSecurityManager = vaultSecurityManager
        )
    }
}
