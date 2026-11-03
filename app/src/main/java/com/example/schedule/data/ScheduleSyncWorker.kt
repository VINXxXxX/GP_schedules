package com.example.schedule.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.schedule.SchedulerCoordinator
import java.util.concurrent.TimeUnit

/** Checks official session times in the background; rebuilds widgets + alerts when they changed. */
class ScheduleSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val r = ScheduleSync.sync(applicationContext)
        if (r.changed) SchedulerCoordinator.onScheduleChanged(applicationContext)
        return if (r.ok) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "SCHEDULE_SYNC_PERIODIC"
        private const val NOW = "SCHEDULE_SYNC_NOW"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 4 h when online; plus a check now if the last one is over an hour old. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ScheduleSyncWorker>(4, TimeUnit.HOURS).setConstraints(online).build()
            )
            if (System.currentTimeMillis() - ScheduleSync.lastChecked(context) > 60 * 60 * 1000L) {
                wm.enqueueUniqueWork(
                    NOW, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ScheduleSyncWorker>().setConstraints(online).build()
                )
            }
        }
    }
}
