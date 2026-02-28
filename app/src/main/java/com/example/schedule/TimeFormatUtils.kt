package com.example.schedule

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

fun formatTime(cal: Calendar): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault())
        .format(cal.time)
}

/**
 * Converts a session time string from IST to the device's local timezone,
 * using [dateCal] to anchor the date so DST rules are applied correctly.
 *
 * Without an anchored date, Java parses against epoch (Jan 1, 1970) where
 * UK was on GMT year-round — causing a 1-hour error during BST (summer).
 *
 * @param istTimeStr  e.g. "09:00 AM" stored in IST
 * @param dateCal     the Calendar for the actual race day (sets year/month/day)
 * @return            formatted time string in device local timezone
 */
fun convertIstToLocal(istTimeStr: String, dateCal: Calendar): String {
    return try {
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val localZone = TimeZone.getDefault()

        // Parse the time-only string
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
            timeZone = istZone
        }
        val parsedTime = timeFmt.parse(istTimeStr.trim()) ?: return istTimeStr

        // Build a full IST Calendar anchored to the race date
        val istCal = Calendar.getInstance(istZone).apply {
            set(Calendar.YEAR,         dateCal.get(Calendar.YEAR))
            set(Calendar.MONTH,        dateCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))

            // Extract H/M from the parsed time using IST zone
            val tmp = Calendar.getInstance(istZone).apply { time = parsedTime }
            set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE,      tmp.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Convert to local
        SimpleDateFormat("hh:mm a", Locale.getDefault()).apply {
            timeZone = localZone
        }.format(istCal.time)

    } catch (_: Exception) {
        istTimeStr
    }
}