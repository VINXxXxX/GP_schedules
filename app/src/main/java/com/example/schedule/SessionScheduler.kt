package com.example.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.WeekendTimeline
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Keeps the widgets (phone + TV) and the TV channel exact: they must redraw at the
 * moment they look different —
 *   • 24 h before a session (day count → ticking countdown)
 *   • a session start (countdown → LIVE + "ENDS IN" 45:00 → 0:00)
 *   • a session end (LIVE → next session)
 *   • local midnight
 *
 * Three layers, because some phones / TVs (vivo, iQOO, Xiaomi, Oppo, many TV boxes)
 * silently drop background alarms — which is what left the timer counting into negatives:
 *   1. exact alarm at the boundary (normal path, on the second)
 *   2. WorkManager one-shot 5 s after the same boundary (backup if the alarm is dropped)
 *   3. during a race weekend only: a redraw every 15 min (last resort)
 */
object SessionScheduler {

    private const val TAG = "SessionScheduler"
    private const val REQUEST_CODE = 9100
    private const val ACTION_WAKE = "com.example.schedule.WIDGET_WAKE"

    private const val BACKUP_WORK = "WIDGET_BOUNDARY_BACKUP"
    private const val TICK_WORK = "RACE_WEEKEND_TICK"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun scheduleNextWakeUp(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val races = RaceRepository.getRaces(app)

        val boundary = WeekendTimeline.nextBoundary(races, now)
        val midnight = nextMidnight(now)
        val triggerAt = if (boundary != null && boundary < midnight) boundary else midnight

        // ---- 1. exact alarm ----
        pendingIntent(app, PendingIntent.FLAG_UPDATE_CURRENT)?.let { pi ->
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                Log.d(TAG, "Next widget refresh at ${Date(triggerAt)}")
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        val wm = WorkManager.getInstance(app)

        // ---- 2. backup one-shot just after the same boundary ----
        wm.enqueueUniqueWork(
            BACKUP_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetTickWorker>()
                .setInitialDelay((triggerAt - now + 5_000L).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
        )

        // ---- 3. every 15 min, race weekend only ----
        val snap = WeekendTimeline.snapshot(races, now)
        val inWeekend = snap != null && !snap.seasonOver &&
                now >= snap.weekendStart - DAY_MS && now < snap.weekendEnd
        if (inWeekend) {
            wm.enqueueUniquePeriodicWork(
                TICK_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetTickWorker>(15, TimeUnit.MINUTES).build()
            )
        } else {
            wm.cancelUniqueWork(TICK_WORK)
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(app, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        WorkManager.getInstance(app).apply {
            cancelUniqueWork(BACKUP_WORK)
            cancelUniqueWork(TICK_WORK)
        }
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_WAKE),
            flag or PendingIntent.FLAG_IMMUTABLE
        )

    /** A few seconds past the next local midnight. */
    private fun nextMidnight(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
}
