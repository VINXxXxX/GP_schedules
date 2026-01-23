package com.example.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object SessionScheduler {

    private const val BASE_REQUEST_CODE = 9100
    private const val MAX_SESSIONS = 20

    fun schedule(
        context: Context,
        race: Race,
        friday: Calendar
    ) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) return

        cancelAll(context)

        race.sessions.forEachIndexed { index, session ->

            if (index >= MAX_SESSIONS) return@forEachIndexed

            val triggerAt =
                buildSessionTimeMillis(session, friday) ?: return@forEachIndexed

            if (triggerAt <= System.currentTimeMillis()) return@forEachIndexed

            val intent = Intent(context, WidgetUpdateReceiver::class.java)
                .putExtra("session_alarm", true)

            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pi
            )
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        repeat(MAX_SESSIONS) { index ->
            val pi = PendingIntent.getBroadcast(
                context,
                BASE_REQUEST_CODE + index,
                Intent(context, WidgetUpdateReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { alarmManager.cancel(it) }
        }
    }

    private fun buildSessionTimeMillis(
        session: Session,
        friday: Calendar
    ): Long? {
        return try {
            val input = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }

            val parsed = input.parse(session.sessionTime.trim()) ?: return null

            val cal = friday.clone() as Calendar
            cal.set(Calendar.HOUR_OF_DAY, parsed.hours)
            cal.set(Calendar.MINUTE, parsed.minutes)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            when (session.sessionName.lowercase()) {
                "fp3", "q1", "q2", "sprint", "sp" ->
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                "race", "race1", "race2" ->
                    cal.add(Calendar.DAY_OF_MONTH, 2)
            }

            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }
}
