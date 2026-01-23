package com.example.schedule

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

        // -------- RACE WEEK --------
        if (motoRace != null && sbkRace != null &&
            sameDay(motoRace.fridayDate(), sbkRace.fridayDate())
        ) {
            scheduleCombinedRaceWeek(context, motoRace, sbkRace)
        } else {
            motoRace?.let { scheduleSingleRaceWeek(context, it, true) }
            sbkRace?.let { scheduleSingleRaceWeek(context, it, false) }
        }

        // -------- DAILY --------
        motoRace?.let { scheduleDaily(context, it, true) }
        sbkRace?.let { scheduleDaily(context, it, false) }
    }

    /* ============================================================
       RACE WEEK (MONDAY 12:00 AM)
       ============================================================ */

    private fun scheduleSingleRaceWeek(
        context: Context,
        race: Race,
        isMotoGP: Boolean
    ) {
        val friday = race.fridayDate()
        val monday = raceWeekMonday(friday)

        if (isPastDay(monday)) return



        schedule(
            context,
            race.round * 100 + if (isMotoGP) 1 else 2,
            monday,
            series(isMotoGP, race),
            "Race week — ${formatDayMonth(friday)}"
        )
    }

    private fun scheduleCombinedRaceWeek(
        context: Context,
        moto: Race,
        sbk: Race
    ) {
        val friday = moto.fridayDate()
        val monday = raceWeekMonday(friday)

        if (isPastDay(monday)) return

        schedule(
            context,
            999_999,
            monday,
            "Race week",
            "MotoGP • ${moto.location.substringAfter(",").uppercase()}\n" +
                    "SBK • ${sbk.location.substringAfter(",").uppercase()}\n" +
                    formatDayMonth(friday)
        )
    }

    /* ============================================================
       DAILY NOTIFICATIONS (RULE BASED)
       ============================================================ */

    private fun scheduleDaily(
        context: Context,
        race: Race,
        isMotoGP: Boolean
    ) {
        val friday = race.fridayDate()
        val now = Calendar.getInstance()

        for (offset in 0..2) { // Fri, Sat, Sun
            val notifyAt = (friday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (notifyAt.before(now)) continue

            val session =
                pickSessionForDay(race.sessions, offset, isMotoGP)
                    ?: continue

            val sessionTime = session.toLocalCalendar(race)

            // 🔑 LABEL OVERRIDE
            val label = when {
                isMotoGP && offset == 0 -> "FP1"
                isMotoGP && offset == 1 -> "FP2"
                isMotoGP && offset == 2 -> "RACE"

                !isMotoGP && offset == 0 -> "FP1"
                !isMotoGP && offset == 1 -> "FP3"
                !isMotoGP && offset == 2 -> "SPR"

                else -> session.sessionName.uppercase()
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
       SESSION PICKER (TIME SOURCE ONLY)
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
            1 -> find("fp3")        // FP3 time used as FP2 for MotoGP
            2 -> if (isMotoGP) find("race") else find("spr")
            else -> null
        }
    }

    /* ============================================================
       CORE ALARM
       ============================================================ */

    private fun schedule(
        context: Context,
        id: Int,
        time: Calendar,
        title: String,
        message: String
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) return

        val intent = Intent(context, RaceNotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("id", id)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            time.timeInMillis,
            pi
        )
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private fun series(isMotoGP: Boolean, race: Race): String =
        (if (isMotoGP) "MotoGP" else "SBK") +
                " • " + race.location.substringAfter(",").uppercase()

    private fun dayName(cal: Calendar): String =
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH)

    private fun raceWeekMonday(friday: Calendar): Calendar =
        (friday.clone() as Calendar).apply {
            // Move back to Monday of the SAME week
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    private fun isPastDay(cal: Calendar): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.before(today)
    }
    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
