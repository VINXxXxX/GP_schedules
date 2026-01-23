@file:Suppress("DEPRECATION")

package com.example.schedule

import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun Session.toLocalCalendar(race: Race): Calendar {

    val raceDate = race.race.split("-")

    val input = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    val date = input.parse(sessionTime.trim()) ?: return Calendar.getInstance()

    return Calendar.getInstance().apply {
        set(
            raceDate[0].toInt(),
            raceDate[1].toInt() - 1,
            raceDate[2].toInt(),
            date.hours,
            date.minutes,
            0
        )
        set(Calendar.MILLISECOND, 0)
    }
}
