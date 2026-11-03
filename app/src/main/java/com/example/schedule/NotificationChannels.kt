package com.example.schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Three channels, one per kind of notification:
 *
 *   reminders  — "FP1 starts in 15 min" (sound + heads-up)
 *   daily      — each weekend day: that day's sessions
 *   preview    — race week: the whole weekend
 */
object NotificationChannels {

    const val REMINDERS = "session_reminders"
    const val DAILY = "daily_schedule"
    const val PREVIEW = "weekend_preview"

    /** Channels from earlier versions, removed on start */
    private val OLD = listOf("race_alerts_v2", "live_session", "quiet_reminders")

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        OLD.forEach { nm.deleteNotificationChannel(it) }

        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, "Before a session starts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A few minutes before each session you picked"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(DAILY, "Today's sessions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Each race-weekend morning: every session that day"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(PREVIEW, "Race week", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Monday of race week: the whole weekend schedule"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
