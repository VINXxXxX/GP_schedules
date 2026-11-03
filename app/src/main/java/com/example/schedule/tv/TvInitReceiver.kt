package com.example.schedule.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.schedule.SchedulerCoordinator

/**
 * Sent by Android TV right after install (android.media.tv.action.INITIALIZE_PROGRAMS):
 * publish the channel row and draw the TV widget without waiting for the app to be opened.
 */
class TvInitReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        SchedulerCoordinator.init(context)
        // WidgetUpdater also refreshes the channel; wait for that before letting the process go
        com.example.schedule.WidgetUpdater.updateAll(context)
        if (!TvChannel.refreshAsync(context) { pending.finish() }) pending.finish()
    }
}
