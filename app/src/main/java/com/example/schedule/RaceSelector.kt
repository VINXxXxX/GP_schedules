package com.example.schedule

import android.content.Context
import com.example.schedule.model.Race
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

object RaceSelector {

    fun getNextRace(context: Context, isMotoGP: Boolean): Race? {

        val file =
            if (isMotoGP) "motogp_races.json" else "sbk_races.json"

        val json = context.assets.open(file)
            .bufferedReader()
            .use { it.readText() }

        val type = object : TypeToken<Map<String, List<Race>>>() {}.type
        val races =
            (Gson().fromJson<Map<String, List<Race>>>(json, type)["races"]
                ?: emptyList())
                .sortedBy { it.fridayDate().timeInMillis }

        if (races.isEmpty()) return null

        val now = Calendar.getInstance()

        for (race in races) {
            val monday = raceWeekMonday(race.fridayDate())
            val endOfWeekend = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 6) // Sunday end
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            // ✅ ONLY Monday → Sunday is race week
            if (now.timeInMillis in monday.timeInMillis..endOfWeekend.timeInMillis) {
                return race
            }

            // ✅ Before Monday → upcoming race
            if (now.before(monday)) {
                return race
            }
        }

        return null
    }

    private fun raceWeekMonday(friday: Calendar): Calendar =
        (friday.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}

