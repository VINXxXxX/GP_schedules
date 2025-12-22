package com.example.schedule.ui

import android.graphics.Color

object ThemeColors {

    // DARK
    const val DARK_TEXT_PRIMARY = "#FFFFFF"
    const val DARK_TEXT_SECONDARY = "#CCCCCC"
    const val DARK_TEXT_MUTED = "#888888"
    const val DARK_CARD_BG = "#1E1E1E"

    // LIGHT
    const val LIGHT_TEXT_PRIMARY = "#000000"
    const val LIGHT_TEXT_SECONDARY = "#444444"
    const val LIGHT_TEXT_MUTED = "#666666"
    const val LIGHT_CARD_BG = "#FFFFFFFF"

    fun textPrimary(dark: Boolean) =
        Color.parseColor(if (dark) DARK_TEXT_PRIMARY else LIGHT_TEXT_PRIMARY)

    fun textSecondary(dark: Boolean) =
        Color.parseColor(if (dark) DARK_TEXT_SECONDARY else LIGHT_TEXT_SECONDARY)

    fun textMuted(dark: Boolean) =
        Color.parseColor(if (dark) DARK_TEXT_MUTED else LIGHT_TEXT_MUTED)

    fun cardBg(dark: Boolean) =
        Color.parseColor(if (dark) DARK_CARD_BG else LIGHT_CARD_BG)
    fun headerText(dark: Boolean) =
        Color.parseColor(if (dark) "#888888" else "#000000")

}

