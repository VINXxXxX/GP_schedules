package com.example.schedule

import java.util.Calendar
import java.util.Locale

fun formatDayMonth(cal: Calendar): String {
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH)
        ?.replaceFirstChar { it.uppercase() }

    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }

    return "$day$suffix $month"
}
