package com.example.schedule.tv

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.example.schedule.SessionScheduler
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.WeekendTimeline

/**
 * Wide MotoGP widget for TV launchers that host Android widgets (ATV Launcher and others).
 * Only enabled on TVs (see [TvSupport.syncComponents]). Drawing lives in [TvWidgetUi].
 */
class TvWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
        SessionScheduler.scheduleNextWakeUp(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        update(context, appWidgetManager, appWidgetId)
    }

    companion object {

        /** Redraws every TV widget. Returns how many are placed. */
        fun updateAll(context: Context, manager: AppWidgetManager?): Int {
            if (manager == null) return 0
            val ids = try {
                manager.getAppWidgetIds(ComponentName(context, TvWidget::class.java))
            } catch (e: Exception) {
                IntArray(0)
            }
            ids.forEach { update(context, manager, it) }
            return ids.size
        }

        fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            try {
                val now = System.currentTimeMillis()
                val races = RaceRepository.getRaces(context)
                val snap = WeekendTimeline.snapshot(races, now)
                val tilesWidth = TvWidgetUi.tilesMinWidth(snap?.sessions?.size ?: 1)

                fun build(mode: TvWidgetUi.Mode) = TvWidgetUi.build(context, races, snap, now, mode)

                val views: RemoteViews =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Android 12+: the launcher picks the layout that fits its actual size
                        RemoteViews(
                            mapOf(
                                SizeF(160f, 80f) to build(TvWidgetUi.Mode.AGENDA),
                                SizeF(maxOf(tilesWidth, 161f), 80f) to build(TvWidgetUi.Mode.TILES)
                            )
                        )
                    } else {
                        // TVs are landscape: the real width is the larger of the two reported
                        val options = manager.getAppWidgetOptions(appWidgetId)
                        val width = maxOf(
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                        )
                        build(if (width >= tilesWidth) TvWidgetUi.Mode.TILES else TvWidgetUi.Mode.AGENDA)
                    }
                manager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("TvWidget", "TV widget update failed", e)
            }
        }
    }
}
