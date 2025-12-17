package com.example.schedule

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkScheduler {

    fun scheduleDailyMidnightWork(context: Context) {

        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }

        val delay = nextMidnight.timeInMillis - now.timeInMillis

        val workRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("MIDNIGHT_WIDGET_REFRESH")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "MIDNIGHT_WIDGET_REFRESH",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
