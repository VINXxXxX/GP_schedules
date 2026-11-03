package com.example.schedule.ui

import android.content.Context
import android.content.res.Configuration

/** App theme: DARK (default), LIGHT, or follow the SYSTEM setting. */
object ThemePrefs {

    enum class Mode { DARK, LIGHT, SYSTEM }

    private const val PREFS = "theme_prefs"
    private const val KEY_DARK = "dark_mode"   // legacy boolean, still read for old installs
    private const val KEY_MODE = "theme_mode"

    fun mode(context: Context): Mode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_MODE, null)?.let { saved ->
            return runCatching { Mode.valueOf(saved) }.getOrDefault(Mode.DARK)
        }
        return if (prefs.getBoolean(KEY_DARK, true)) Mode.DARK else Mode.LIGHT
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    fun isDark(context: Context): Boolean = when (mode(context)) {
        Mode.DARK -> true
        Mode.LIGHT -> false
        Mode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
    }

    /** Kept for older callers */
    fun setDark(context: Context, dark: Boolean) =
        setMode(context, if (dark) Mode.DARK else Mode.LIGHT)
}
