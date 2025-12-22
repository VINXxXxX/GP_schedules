package com.example.schedule.ui

import android.content.Context

object ThemePrefs {

    private const val PREFS = "theme_prefs"
    private const val KEY_DARK = "dark_mode"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, true) // DARK default

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
    }
}
