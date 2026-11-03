package com.example.schedule

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Backup redraw for the widgets / TV channel (see SessionScheduler). Redrawing also
 * re-arms the next exact alarm, so one successful run puts everything back on time.
 */
class WidgetTickWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}
