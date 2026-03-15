@file:Suppress("DEPRECATION")

package com.example.schedule

import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun Session.toLocalCalendar(race: Race): Calendar {

    val raceParts = race.race.split("-")
    val baseDate = Calendar.getInstance().apply {
        set(raceParts[0].toInt(), raceParts[1].toInt() - 1, raceParts[2].toInt(), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // Advance by dayOffset days to get the actual session date
    baseDate.add(Calendar.DAY_OF_MONTH, dayOffset)

    val year  = baseDate.get(Calendar.YEAR)
    val month = baseDate.get(Calendar.MONTH) + 1
    val day   = baseDate.get(Calendar.DAY_OF_MONTH)
    val dateStr = "%04d-%02d-%02d".format(year, month, day)

    val input = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }

    val date = input.parse("$dateStr ${sessionTime.trim()}") ?: return Calendar.getInstance()

    val cal = Calendar.getInstance()
    cal.time = date
    return cal
}