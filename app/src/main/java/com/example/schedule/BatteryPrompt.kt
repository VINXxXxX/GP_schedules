package com.example.schedule

import android.annotation.SuppressLint
import android.content.Context

object BatteryPrompt {

    private const val PREFS = "battery_prompt"
    private const val KEY_SHOWN = "shown"

    fun shouldShow(context: Context): Boolean {
        return !context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOWN, false)
    }

    @SuppressLint("UseKtx")
    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOWN, true)
            .apply()
    }
}
