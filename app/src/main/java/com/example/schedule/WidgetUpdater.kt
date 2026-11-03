package com.example.schedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.schedule.tv.TvChannel
import com.example.schedule.tv.TvWidget

object WidgetUpdater {

    fun updateAll(context: Context) {
        // Null on TVs whose firmware has no widget support
        val manager: AppWidgetManager? = try {
            AppWidgetManager.getInstance(context)
        } catch (_: Exception) {
            null
        }

        val ids = try {
            manager?.getAppWidgetIds(ComponentName(context, MGPWidget::class.java))
        } catch (_: Exception) {
            null
        } ?: IntArray(0)

        if (manager != null) ids.forEach { MGPWidget.updateAppWidget(context, manager, it) }

        // TV: wide widget (launchers that host widgets) + the channel row on the home screen
        val tvWidgets = TvWidget.updateAll(context, manager)
        val tvChannel = TvChannel.refreshAsync(context)

        // Chain the next exact refresh (session start/end, midnight) — only while something shows it
        if (ids.isNotEmpty() || tvWidgets > 0 || tvChannel) {
            SessionScheduler.scheduleNextWakeUp(context)
        } else {
            SessionScheduler.cancel(context)
        }
    }
}
