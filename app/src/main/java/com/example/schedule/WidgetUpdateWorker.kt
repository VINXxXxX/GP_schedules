package com.example.schedule

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.appwidget.AppWidgetManager
import android.content.ComponentName

class WidgetUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        // Update MGP Widget
        val mgpManager = AppWidgetManager.getInstance(applicationContext)
        val mgpIds = mgpManager.getAppWidgetIds(ComponentName(applicationContext, MGPWidget::class.java))
        for (id in mgpIds) {
            MGPWidget.updateAppWidget(applicationContext, mgpManager, id)
        }

        // Update SBK Widget
        val sbkManager = AppWidgetManager.getInstance(applicationContext)
        val sbkIds = sbkManager.getAppWidgetIds(ComponentName(applicationContext, SBKWidget::class.java))
        for (id in sbkIds) {
            SBKWidget.updateAppWidget(applicationContext, sbkManager, id)
        }

        return Result.success()
    }
}