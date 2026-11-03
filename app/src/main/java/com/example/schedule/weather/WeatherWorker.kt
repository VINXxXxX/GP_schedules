package com.example.schedule.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.schedule.WidgetUpdater
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.WeekendTimeline

/** Fetches race-week weather once a day when there's a network, then redraws widgets. */
class WeatherWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val now = System.currentTimeMillis()
        val race = WeekendTimeline.snapshot(RaceRepository.getRaces(applicationContext), now)?.race
            ?: return Result.success()
        if (!Weather.inRaceWeek(race, now)) return Result.success()
        if (!Weather.needsRefresh(applicationContext, race, now)) return Result.success()

        return if (Weather.fetch(applicationContext, race, now)) {
            WidgetUpdater.updateAll(applicationContext)
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        /** Cheap to call often: does nothing outside race week or if today's forecast is cached. */
        fun enqueue(context: Context) {
            val now = System.currentTimeMillis()
            val race = WeekendTimeline.snapshot(RaceRepository.getRaces(context), now)?.race ?: return
            if (!Weather.inRaceWeek(race, now) || !Weather.needsRefresh(context, race, now)) return

            val request = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("RACE_WEEK_WEATHER", ExistingWorkPolicy.KEEP, request)
        }
    }
}
