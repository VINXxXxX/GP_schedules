package com.example.schedule

import android.content.Context

object NotificationPrefs {

    private const val PREFS = "notification_prefs"
    private const val KEY_MOTOGP = "notify_motogp"
    private const val KEY_SBK = "notify_sbk"

    fun isMotoGPEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MOTOGP, true)

    fun isSBKEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SBK, true)

    fun setMotoGP(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MOTOGP, enabled).apply()
    }

    fun setSBK(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SBK, enabled).apply()
    }
}
