@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.example.schedule

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.util.Calendar
import java.util.Locale

object NotificationScheduler {

    fun scheduleForNextRace(context: Context) {

        val motoRace =
            if (NotificationPrefs.isMotoGPEnabled(context))
                RaceSelector.getNextRace(context, true)
            else null

        val sbkRace =
            if (NotificationPrefs.isSBKEnabled(context))
                RaceSelector.getNextRace(context, false)
            else null

        motoRace?.let { scheduleRaceWeek(context, it, true) }
        sbkRace?.let { scheduleRaceWeek(context, it, false) }

        motoRace?.let { scheduleDaily(context, it, true) }
        sbkRace?.let { scheduleDaily(context, it, false) }
    }

    /* ============================================================
       RACE WEEK — MONDAY ONLY
       ============================================================ */

    private fun scheduleRaceWeek(
        context: Context,
        race: Race,
        isMotoGP: Boolean
    ) {
        val friday = race.fridayDate()
        val monday = raceWeekMonday(friday)

        // 🔒 ONLY schedule on Monday of race week
        if (monday.before(Calendar.getInstance())) return


        val raceId = race.round * 100 + if (isMotoGP) 1 else 2
        if (RaceWeekGuard.alreadyNotified(context, raceId)) return

        schedule(
            context,
            raceId,
            monday,
            series(isMotoGP, race),
            "Race week — ${formatDayMonth(friday)}"
        )

        RaceWeekGuard.markNotified(context, raceId)
    }


    /* ============================================================
       DAILY NOTIFICATIONS
       ============================================================ */

    private fun scheduleDaily(
        context: Context,
        race: Race,
        isMotoGP: Boolean
    ) {
        val friday = race.fridayDate()
        val now = Calendar.getInstance()

        for (offset in 0..2) {

            val notifyAt = (friday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (notifyAt.before(now)) continue

            val session =
                pickSessionForDay(race.sessions, offset, isMotoGP) ?: continue

            val sessionTime = session.toLocalCalendar(race)

            val label = when {
                isMotoGP && offset == 0 -> "FP1"
                isMotoGP && offset == 1 -> "FP2"
                isMotoGP -> "RACE"
                !isMotoGP && offset == 0 -> "FP1"
                !isMotoGP && offset == 1 -> "FP3"
                else -> "SPR"
            }

            val id =
                race.round * 10_000 +
                        notifyAt.get(Calendar.DAY_OF_YEAR) +
                        if (isMotoGP) 1 else 2

            schedule(
                context,
                id,
                notifyAt,
                series(isMotoGP, race),
                "${dayName(notifyAt)} — $label at ${formatTime(sessionTime)}"
            )
        }
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private fun raceWeekMonday(friday: Calendar): Calendar =
        (friday.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun series(isMotoGP: Boolean, race: Race): String =
        (if (isMotoGP) "MotoGP" else "SBK") +
                " • " + race.location.substringAfter(",").uppercase()

    private fun dayName(cal: Calendar): String =
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH)

    /* ============================================================
       CORE ALARM
       ============================================================ */

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        id: Int,
        time: Calendar,
        title: String,
        message: String
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, RaceNotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                time.timeInMillis,
                pendingIntent
            )
        }
    }



    /* ============================================================
       SESSION PICKER
       ============================================================ */

    private fun pickSessionForDay(
        sessions: List<Session>,
        dayOffset: Int,
        isMotoGP: Boolean
    ): Session? {

        fun find(vararg names: String): Session? =
            sessions.firstOrNull { s ->
                names.any { it.equals(s.sessionName, true) }
            }

        return when (dayOffset) {
            0 -> find("fp1")
            1 -> find("fp3")
            2 -> if (isMotoGP) find("race") else find("spr")
            else -> null
        }
    }
}
