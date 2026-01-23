package com.example.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {

    private const val PREFS = "notification_permission"
    private const val KEY_ASKED = "asked"

    fun maybeRequest(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return

        val granted = hasPermission(activity)

        if (granted) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            return
        }

        // ---- PRE-PERMISSION RATIONALE ----
        AlertDialog.Builder(activity)
            .setTitle("Enable notifications")
            .setMessage(
                "We use notifications to remind you about race weeks and " +
                        "daily race schedules for MotoGP and SBK.\n\n" +
                        "You can change this anytime in Settings."
            )
            .setPositiveButton("Allow") { _, _ ->
                prefs.edit().putBoolean(KEY_ASKED, true).apply()

                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
            }
            .setNegativeButton("Not now") { _, _ ->
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
            }
            .show()
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openSettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        activity.startActivity(intent)
    }
}
