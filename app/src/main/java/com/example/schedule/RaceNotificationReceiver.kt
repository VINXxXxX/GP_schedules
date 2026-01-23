package com.example.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class RaceNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // Android 13+ permission guard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

            if (!granted) return
        }

        val title = intent.getStringExtra("title") ?: return
        val message = intent.getStringExtra("message") ?: return
        val id = intent.getIntExtra("id", 0)

        val builder = NotificationCompat.Builder(
            context,
            NotificationChannels.RACE_ALERTS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // ----- MULTI-LINE SUPPORT -----
        if (message.contains("\n")) {
            val lines = message.split("\n")

            val style = NotificationCompat.InboxStyle()
            lines.dropLast(1).forEach { style.addLine(it) }
            style.setSummaryText(lines.last())

            builder.setStyle(style)
        } else {
            builder.setContentText(message)
        }

        NotificationManagerCompat.from(context)
            .notify(id, builder.build())
    }
}
