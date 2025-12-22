package com.example.schedule

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.appcompat.app.AlertDialog

object BatteryOptimizationHelper {

    /* ---------- OEM DETECTION ---------- */

    private val manufacturer: String =
        Build.MANUFACTURER.lowercase()

    fun isXiaomi() = manufacturer.contains("xiaomi") || manufacturer.contains("redmi")
    fun isVivo() = manufacturer.contains("vivo")
    fun isOppo() = manufacturer.contains("oppo")
    fun isRealme() = manufacturer.contains("realme")
    fun isSamsung() = manufacturer.contains("samsung")

    fun isAggressiveOEM(): Boolean =
        isXiaomi() || isVivo() || isOppo() || isRealme() || isSamsung()

    /* ---------- ENTRY POINT ---------- */

    fun maybeShowPrompt(activity: Activity) {
        if (!isAggressiveOEM()) return
        if (!BatteryPrompt.shouldShow(activity)) return

        val message = buildMessage()

        AlertDialog.Builder(activity)
            .setTitle("Allow background updates")
            .setMessage(message)
            .setCancelable(false) // GitHub-only → allowed
            .setPositiveButton("Open settings") { _, _ ->
                BatteryPrompt.markShown(activity)
                openOEMSettings(activity)
                requestIgnoreBatteryOptimizations(activity)
            }
            .setNegativeButton("Later") { _, _ ->
                BatteryPrompt.markShown(activity)
            }
            .show()
    }

    /* ---------- MESSAGE ---------- */

    private fun buildMessage(): String {
        return when {
            isVivo() ->
                "Vivo aggressively blocks widget updates.\n\n" +
                        "To keep race countdown widgets accurate:\n\n" +
                        "• Settings → Battery → Background power consumption\n" +
                        "• Allow this app\n\n" +
                        "Without this, widgets update only when tapped."

            isXiaomi() ->
                "Xiaomi/Redmi blocks background tasks.\n\n" +
                        "Required steps:\n\n" +
                        "• Security app → Battery → App battery saver\n" +
                        "• Set this app to No restrictions\n\n" +
                        "Otherwise widgets may stop updating."

            isOppo() || isRealme() ->
                "Oppo / Realme restrict background apps.\n\n" +
                        "Steps:\n\n" +
                        "• Settings → Battery → App battery usage\n" +
                        "• Allow background activity\n\n" +
                        "Without this, widgets may freeze."

            isSamsung() ->
                "Samsung may put this app to sleep.\n\n" +
                        "Steps:\n\n" +
                        "• Settings → Battery → Background usage limits\n" +
                        "• Remove this app from Sleeping apps\n\n" +
                        "Otherwise widgets may not update."

            else ->
                "Allow background activity to keep widgets updated."
        }
    }

    /* ---------- OEM SETTINGS ---------- */

    @SuppressLint("UseKtx")
    private fun openOEMSettings(context: Context) {
        try {
            val intent = when {
                isVivo() ->
                    Intent("com.vivo.permissionmanager")

                isXiaomi() ->
                    Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
                        .putExtra("package_name", context.packageName)

                isOppo() || isRealme() ->
                    Intent("com.coloros.safecenter")

                isSamsung() ->
                    Intent().apply {
                        action = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.parse("package:${context.packageName}")
                    }

                else -> null
            }

            intent?.let { context.startActivity(it) }

        } catch (_: Exception) {
            // Ignore – user can still manually fix
        }
    }

    /* ---------- SYSTEM BATTERY OPT ---------- */

    @SuppressLint("BatteryLife", "UseKtx", "ObsoleteSdkInt")
    private fun requestIgnoreBatteryOptimizations(activity: Activity) {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !pm.isIgnoringBatteryOptimizations(activity.packageName)
        ) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
