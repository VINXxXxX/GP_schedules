package com.example.schedule

import android.annotation.SuppressLint
import android.content.Context

object RaceWeekGuard {

    private const val PREFS = "race_week_guard"
    private const val KEY_LAST_NOTIFIED = "last_race_week_id"

    fun alreadyNotified(context: Context, raceId: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LAST_NOTIFIED, -1) == raceId
    }

    @SuppressLint("UseKtx")
    fun markNotified(context: Context, raceId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_NOTIFIED, raceId)
            .apply()
    }
}
