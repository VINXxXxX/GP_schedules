package com.example.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.model.Race
import java.util.Calendar

/**
 * Three kinds of notification, nothing else:
 *
 *   WEEK      Monday 09:00 of race week → the whole weekend, every session
 *   DAY       each weekend day, 07:00 (or 1 h before the first session if earlier) → that day's sessions
 *   REMINDER  lead time before each session the user picked ("FP1 starts in 15 min")
 *
 * Alarms are set for the current and next round. The text is built when the alarm fires
 * (RaceNotificationReceiver), so an imported schedule or changed setting is always reflected.
 * Safe to call any time: it clears its old alarms first.
 */
object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    private const val PREFS = "notification_scheduler"
    private const val KEY_SCHEDULED_IDS = "scheduled_ids"

    const val TYPE_REMINDER = 1
    /** At session start: removes that session's reminder (backup for phones that ignore timeouts) */
    const val TYPE_CLEAR = 2
    const val TYPE_WEEK = 3
    const val TYPE_DAY = 4

    /** Older name kept so nothing else breaks */
    const val TYPE_PREVIEW = TYPE_WEEK

    const val EXTRA_TYPE = "type"
    const val EXTRA_RACE = "race_key"
    const val EXTRA_SESSION = "session"
    const val EXTRA_ID = "id"
    const val EXTRA_DAY = "day_key"

    /** Kept name for older callers */
    fun scheduleForNextRace(context: Context) = rescheduleAll(context)

    fun rescheduleAll(context: Context) {
        cancelAll(context)

        if (!NotificationPrefs.isMotoGPEnabled(context)) return
        if (NotificationPermissionHelper.notificationsDisabled(context)) return

        val now = System.currentTimeMillis()
        val lead = NotificationPrefs.leadMinutes(context) * 60_000L

        val races = RaceRepository.getRaces(context)
            .filter { WeekendTimeline.weekendEnd(it) > now }
            .take(2)

        races.forEach { race ->
            val sessions = WeekendTimeline.build(race)

            // ---- before each session ----
            sessions.forEach { ts ->
                if (!NotificationPrefs.isSessionOn(context, race, ts.session)) return@forEach
                val remindAt = ts.start - lead
                if (remindAt > now) {
                    schedule(context, alarmId(race, ts, TYPE_REMINDER), remindAt, TYPE_REMINDER, race, ts, null)
                }
                if (ts.start > now) {
                    schedule(context, alarmId(race, ts, TYPE_CLEAR), ts.start, TYPE_CLEAR, race, ts, null)
                }
            }

            // ---- each weekend day ----
            if (NotificationPrefs.dailySchedule(context)) {
                sessions.groupBy { dayKey(it.start) }.entries.forEachIndexed { d, (key, list) ->
                    val at = dayTime(list.minOf { it.start })
                    if (at > now) schedule(context, dayId(race, d), at, TYPE_DAY, race, null, key)
                }
            }

            // ---- race week ----
            if (NotificationPrefs.weekendPreview(context)) {
                val at = weekTime(sessions)
                if (at != null && at > now) {
                    schedule(context, previewId(race), at, TYPE_WEEK, race, null, null)
                }
            }
        }
    }

    fun dayKey(millis: Long): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ENGLISH).format(millis)

    /** 07:00 local that day, or 1 h before the first session if it starts before 08:00 */
    private fun dayTime(firstStart: Long): Long {
        val seven = Calendar.getInstance().apply {
            timeInMillis = firstStart
            set(Calendar.HOUR_OF_DAY, 7); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return minOf(seven, firstStart - 60 * 60_000L)
    }

    /** Monday 09:00 local of the week the weekend starts in */
    private fun weekTime(sessions: List<TimedSession>): Long? {
        val first = sessions.minOfOrNull { it.start } ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = first
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis.coerceAtMost(first - 60 * 60_000L)
    }

    /** Cancels every alarm this class has set. */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty()

        ids.mapNotNull { it.toIntOrNull() }.forEach { id ->
            PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, RaceNotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        prefs.edit().remove(KEY_SCHEDULED_IDS).apply()
    }

    // ------------------------------------------------------------------
    // IDS
    // ------------------------------------------------------------------

    fun raceKey(race: Race) = "${race.race}|${race.slug}"

    private fun sessionIndex(race: Race, ts: TimedSession) =
        race.sessions.indexOf(ts.session).coerceAtLeast(0)

    fun notificationId(race: Race, ts: TimedSession): Int =
        (race.round % 100) * 1000 + sessionIndex(race, ts) * 10

    private fun alarmId(race: Race, ts: TimedSession, type: Int) = notificationId(race, ts) + type

    fun previewId(race: Race): Int = (race.round % 100) * 1000 + 999

    fun dayId(race: Race, dayIndex: Int): Int = (race.round % 100) * 1000 + 900 + dayIndex

    // ------------------------------------------------------------------

    private fun rememberId(context: Context, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet()).orEmpty().toMutableSet()
        if (ids.add(id.toString())) prefs.edit().putStringSet(KEY_SCHEDULED_IDS, ids).apply()
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(context: Context, id: Int, at: Long, type: Int, race: Race, ts: TimedSession?, day: String?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, RaceNotificationReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_RACE, raceKey(race))
            putExtra(EXTRA_SESSION, ts?.session?.sessionName)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_DAY, day)
        }
        val pi = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        rememberId(context, id)
    }
}
