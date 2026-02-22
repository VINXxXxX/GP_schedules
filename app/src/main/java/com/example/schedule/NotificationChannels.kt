package com.example.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {

    const val RACE_ALERTS = "race_alerts_v2"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                RACE_ALERTS,
                "Race schedule alerts",
                NotificationManager.IMPORTANCE_LOW // 🔕 Silent
            ).apply {
                description = "Race week and daily schedule alerts"

                // Silence
                setSound(null, null)
                enableVibration(false)
                enableLights(false)

                // Lock screen behavior
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            context
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

