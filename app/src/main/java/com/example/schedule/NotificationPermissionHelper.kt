package com.example.schedule

import android.annotation.SuppressLint
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

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Ask permission ONCE on first app launch (Android 13+)
     */
    @SuppressLint("UseKtx")
    fun maybeRequest(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return

        if (hasPermission(activity)) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("Enable notifications")
            .setMessage(
                "Allow notifications to receive MotoGP and SBK race alerts."
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

    /**
     * TRUE only when user has explicitly BLOCKED notifications
     */
    fun isBlocked(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (hasPermission(activity)) return false

        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    }

    fun openSettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        activity.startActivity(intent)
    }
    fun notificationsDisabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

}
