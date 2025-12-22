package com.example.schedule.ui

import android.app.Activity
import androidx.core.content.ContextCompat
import com.example.schedule.R

object ThemeApplier {

    fun apply(activity: Activity) {

        val dark = ThemePrefs.isDark(activity)

        val res = activity.resources

        val textPrimary =
            if (dark) "#FFFFFF" else "#000000"
        val textSecondary =
            if (dark) "#CCCCCC" else "#444444"
        val textMuted =
            if (dark) "#888888" else "#666666"
        val cardBg =
            if (dark) "#1E1E1E" else "#FFFFFF"

        // Colors are read via resources → activity recreate applies them
    }
}
