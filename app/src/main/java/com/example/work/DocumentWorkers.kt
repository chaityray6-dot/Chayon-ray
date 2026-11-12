package com.example.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.DocuVerseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DocumentIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? DocuVerseApp ?: return@withContext Result.success()
        app.container.documentRepository.seedStarterLibraryIfEmpty()
        app.container.documentRepository.findDuplicateGroups()
        Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<DocumentIndexWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "docuverse_index_work",
                ExistingWorkPolicy.KEEP,
                req
            )
        }
    }
}
