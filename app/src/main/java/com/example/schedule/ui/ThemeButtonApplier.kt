package com.example.schedule.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.example.schedule.R

object ThemeButtonApplier {

    fun apply(context: Context, dark: Boolean) {
        val res = context.resources
        val editor =
            context.getSharedPreferences("theme_runtime", Context.MODE_PRIVATE)
                .edit()

        if (dark) {
            editor.putInt("btn_bg", 0xFF1C1C1C.toInt())
            editor.putInt("btn_stroke", 0xFFFF3B3B.toInt())
            editor.putInt("btn_ripple", 0x40FF3B3B)
        } else {
            editor.putInt("btn_bg", 0xFFFFFFFF.toInt())
            editor.putInt("btn_stroke", 0xFFFFFFFF.toInt())
            editor.putInt("btn_ripple", 0x33000000)
        }

        editor.apply()
    }
}
