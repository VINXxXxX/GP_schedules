package com.example.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Log.d("TimeChange", "Time change detected: ${intent.action}")

        // Refresh widgets + alarms
        SchedulerCoordinator.forceRefresh(context)

        // 🔔 Re-schedule notifications
        NotificationScheduler.scheduleForNextRace(context)

    }
}
