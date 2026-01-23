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
                .sortedBy { it.race }

        if (races.isEmpty()) return null

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (race in races) {
            val friday = race.fridayDate()
            val mondayAfter = (friday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 3)
            }

            if (today.timeInMillis < friday.timeInMillis ||
                today.timeInMillis in friday.timeInMillis until mondayAfter.timeInMillis
            ) {
                return race
            }
        }

        return races.last()
    }
}
