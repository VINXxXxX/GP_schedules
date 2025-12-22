package com.example.schedule.utils

import java.util.Calendar

object RaceDateFormatter {

    fun formatWeekend(friday: Calendar): String {

        val sunday = (friday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 2)
        }

        val startDay = friday.get(Calendar.DAY_OF_MONTH)
        val endDay = sunday.get(Calendar.DAY_OF_MONTH)

        val month = arrayOf(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
        )[sunday.get(Calendar.MONTH)] // ✅ END MONTH ONLY

        return if (startDay == endDay) {
            "$startDay $month"
        } else {
            "$startDay–$endDay $month"
        }
    }
}
