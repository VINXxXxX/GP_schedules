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
        SchedulerCoordinator.init(applicationContext)
        return Result.success()
    }
}
