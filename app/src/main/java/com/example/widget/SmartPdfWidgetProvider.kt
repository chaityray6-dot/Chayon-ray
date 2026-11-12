package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class SmartPdfWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_smart_pdf).apply {
                setOnClickPendingIntent(
                    R.id.widget_btn_scan,
                    createActionPendingIntent(context, ACTION_SCAN, REQUEST_SCAN)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_search,
                    createActionPendingIntent(context, ACTION_SEARCH, REQUEST_SEARCH)
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_import,
                    createActionPendingIntent(context, ACTION_IMPORT, REQUEST_IMPORT)
                )
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createActionPendingIntent(
        context: Context,
        actionValue: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = when (actionValue) {
                ACTION_SCAN -> INTENT_ACTION_SCAN
                ACTION_SEARCH -> INTENT_ACTION_SEARCH
                else -> INTENT_ACTION_IMPORT
            }
            putExtra(EXTRA_WIDGET_ACTION, actionValue)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_WIDGET_ACTION = "com.example.EXTRA_WIDGET_ACTION"
        const val ACTION_SCAN = "scan"
        const val ACTION_SEARCH = "search"
        const val ACTION_IMPORT = "import"

        const val INTENT_ACTION_SCAN = "com.example.ACTION_WIDGET_SCAN"
        const val INTENT_ACTION_SEARCH = "com.example.ACTION_WIDGET_SEARCH"
        const val INTENT_ACTION_IMPORT = "com.example.ACTION_WIDGET_IMPORT"

        private const val REQUEST_SCAN = 101
        private const val REQUEST_SEARCH = 102
        private const val REQUEST_IMPORT = 103
    }
}
