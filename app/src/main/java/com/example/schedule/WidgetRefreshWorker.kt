package com.example.schedule

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {

        WidgetUpdater.updateAll(applicationContext)
        // Also rebuilds notification alarms, so the next round gets its alerts
        SchedulerCoordinator.init(applicationContext)

        return Result.success()
    }
}
