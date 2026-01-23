package com.example.schedule

import android.content.Context

object SchedulerCoordinator {

    fun init(context: Context) {
        AlarmScheduler.scheduleNextMidnightUpdate(context)
        WorkScheduler.scheduleDailyMidnightWork(context)
        NotificationScheduler.scheduleForNextRace(context)


    }

    fun forceRefresh(context: Context) {
        WidgetUpdater.updateAll(context)
        init(context)
        NotificationScheduler.scheduleForNextRace(context)
    }
}
