package com.example.schedule

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, MGPWidget::class.java)
        ).forEach {
            MGPWidget.updateAppWidget(context, appWidgetManager, it)
        }

        appWidgetManager.getAppWidgetIds(
            ComponentName(context, SBKWidget::class.java)
        ).forEach {
            SBKWidget.updateAppWidget(context, appWidgetManager, it)
        }

        SchedulerCoordinator.init(context)
    }

}