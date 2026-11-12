package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.di.AppContainer
import com.example.work.DocumentIndexWorker
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class DocuVerseApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        runCatching {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, workManagerConfiguration)
            }
            DocumentIndexWorker.enqueue(this)
        }
    }
}
