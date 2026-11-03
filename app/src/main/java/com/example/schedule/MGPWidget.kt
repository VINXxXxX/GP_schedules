package com.example.schedule

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.widget.WidgetUi
import com.example.schedule.widget.WidgetUi.Mode

/** The 4×2 MotoGP widget. Drawing lives in [WidgetUi]. */
class MGPWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
        SessionScheduler.scheduleNextWakeUp(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {

        // Height breakpoints (dp)
        private const val MEDIUM_FROM = 150f
        private const val TALL_FROM = 215f

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            try {
                val now = System.currentTimeMillis()
                val races = RaceRepository.getRaces(context)
                val snap = WeekendTimeline.snapshot(races, now)

                fun build(mode: Mode) =
                    WidgetUi.build(context, R.layout.widget_mgp, races, snap, now, mode)

                val views: RemoteViews =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+: hand the launcher one layout per size, it picks the best fit
                        RemoteViews(
                            mapOf(
                                SizeF(250f, 100f) to build(Mode.SHORT),
                                SizeF(250f, MEDIUM_FROM) to build(Mode.MEDIUM),
                                SizeF(250f, TALL_FROM) to build(Mode.TALL)
                            )
                        )
                    } else {
                        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                        // Portrait height is reported as MAX_HEIGHT
                        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                        build(
                            when {
                                height >= TALL_FROM -> Mode.TALL
                                height >= MEDIUM_FROM || height == 0 -> Mode.MEDIUM
                                else -> Mode.SHORT
                            }
                        )
                    }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("MGPWidget", "Widget update failed", e)
            }
        }
    }
}
