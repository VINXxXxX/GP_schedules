package com.example.schedule

import com.example.schedule.model.Race
import java.util.Calendar

/**
 * Returns the Friday date of the race as a Calendar at 00:00
 * race.race format: yyyy-MM-dd
 */
fun Race.fridayDate(): Calendar {

    val parts = race.split("-")

    return Calendar.getInstance().apply {
        set(
            parts[0].toInt(),          // year
            parts[1].toInt() - 1,      // month (0-based)
            parts[2].toInt(),          // day
            0, 0, 0
        )
        set(Calendar.MILLISECOND, 0)
    }
}
