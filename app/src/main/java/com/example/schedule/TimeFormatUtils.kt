package com.example.schedule

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun formatTime(cal: Calendar): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault())
        .format(cal.time)
}
