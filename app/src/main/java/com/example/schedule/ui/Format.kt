package com.example.schedule.ui

import android.content.Context
import android.text.format.DateFormat
import com.example.schedule.data.TimedSession
import com.example.schedule.fridayDate
import com.example.schedule.model.Race
import com.example.schedule.utils.RaceDateFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Times and dates in the phone's own time zone and 12/24 h setting. */
object Format {

    private val MONTHS = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    fun clock(context: Context, millis: Long): String =
        if (DateFormat.is24HourFormat(context)) {
            SimpleDateFormat("HH:mm", Locale.ENGLISH).format(millis)
        } else {
            SimpleDateFormat("h:mm a", Locale.ENGLISH).format(millis).uppercase(Locale.ENGLISH)
        }

    /** "FRI 05" */
    fun dayLabel(millis: Long): String =
        SimpleDateFormat("EEE dd", Locale.ENGLISH).format(millis).uppercase(Locale.ENGLISH)

    /** "FRI" */
    fun dayShort(millis: Long): String =
        SimpleDateFormat("EEE", Locale.ENGLISH).format(millis).uppercase(Locale.ENGLISH)

    /** "12 MAR" */
    fun dayMonth(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.DAY_OF_MONTH)} ${MONTHS[c.get(Calendar.MONTH)]}"
    }

    /**
     * Weekend dates in local days: "5–7 MAR", "30 MAR–1 APR".
     * Falls back to the JSON's Friday when no session has a time.
     */
    fun weekendRange(race: Race, sessions: List<TimedSession>): String {
        if (sessions.isEmpty()) return RaceDateFormatter.formatWeekend(race.fridayDate())
        val a = Calendar.getInstance().apply { timeInMillis = sessions.minOf { it.start } }
        val b = Calendar.getInstance().apply { timeInMillis = sessions.maxOf { it.start } }
        val d1 = a.get(Calendar.DAY_OF_MONTH)
        val d2 = b.get(Calendar.DAY_OF_MONTH)
        val m1 = MONTHS[a.get(Calendar.MONTH)]
        val m2 = MONTHS[b.get(Calendar.MONTH)]
        return when {
            m1 != m2 -> "$d1 $m1 – $d2 $m2"
            d1 == d2 -> "$d1 $m2"
            else -> "$d1–$d2 $m2"
        }
    }

    /** "in 4h 15m", "in 3 days", "in 12m" */
    fun relative(now: Long, target: Long): String {
        val mins = ((target - now) / 60_000L).coerceAtLeast(0)
        val days = mins / (60 * 24)
        return when {
            days >= 2 -> "in $days days"
            mins >= 60 -> "in ${mins / 60}h ${mins % 60}m"
            mins >= 1 -> "in ${mins}m"
            else -> "starting"
        }
    }

    /** "25 min left" */
    fun left(now: Long, end: Long): String {
        val mins = ((end - now + 59_999L) / 60_000L).coerceAtLeast(0)
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m left" else "$mins min left"
    }

    fun city(race: Race): String =
        race.location.substringBefore(",").trim()
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .uppercase(Locale.ENGLISH)

    fun country(race: Race): String =
        race.location.substringAfter(",", "").trim().uppercase(Locale.ENGLISH)

    /** Calendar days between two instants, local time. */
    fun calendarDays(now: Long, target: Long): Int {
        fun midnight(t: Long) = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((midnight(target) - midnight(now)) / 86_400_000.0).toInt()
    }
}
