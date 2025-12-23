package com.example.schedule

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {

fun updateAll(context: Context) {
    val manager = AppWidgetManager.getInstance(context)

    manager.getAppWidgetIds(ComponentName(context, MGPWidget::class.java))
            .forEach { MGPWidget.updateAppWidget(context, manager, it) }

    manager.getAppWidgetIds(ComponentName(context, SBKWidget::class.java))
            .forEach { SBKWidget.updateAppWidget(context, manager, it) }
    manager.getAppWidgetIds(ComponentName(context, MGPWidgetCompact::class.java))
            .forEach { MGPWidgetCompact.updateAppWidget(context,manager,it) }
    manager.getAppWidgetIds(ComponentName(context, SBKWidgetCompact::class.java))
            .forEach { SBKWidgetCompact.updateAppWidget(context,manager,it) }
}
}
