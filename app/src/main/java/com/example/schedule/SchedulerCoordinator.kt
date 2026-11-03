package com.example.schedule

import android.content.Context

object SchedulerCoordinator {

    fun init(context: Context) {
        AlarmScheduler.scheduleNextMidnightUpdate(context)
        WorkScheduler.scheduleDailyMidnightWork(context)
        NotificationScheduler.rescheduleAll(context)
        com.example.schedule.weather.WeatherWorker.enqueue(context)
        // Official session times (GitHub file → MotoGP feed)
        com.example.schedule.data.ScheduleSyncWorker.schedule(context)
    }

    fun forceRefresh(context: Context) {
        WidgetUpdater.updateAll(context)
        init(context)
    }

    /**
     * Called after a schedule is imported or reset. Clears alarms built from the
     * old data so nothing stale fires, then rebuilds widgets and notifications.
     */
    fun onScheduleChanged(context: Context) {
        WidgetUpdater.updateAll(context)
        NotificationScheduler.rescheduleAll(context)
    }
}
