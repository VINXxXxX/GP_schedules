package com.example.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class WidgetRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.getBooleanExtra("haptic", false)) {

            val vibrator = context.getSystemService(Vibrator::class.java)

            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            30,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(30)
                }
            }
        }

        WidgetUpdater.updateAll(context)
        // Tap also pulls today's track weather in race week (if not fetched yet today)
        com.example.schedule.weather.WeatherWorker.enqueue(context)
    }
}
