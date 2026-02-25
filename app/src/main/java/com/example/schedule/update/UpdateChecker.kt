package com.example.schedule.update

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val OWNER = "VINXxXxX"
    private const val REPO = "GP_schedules"

    fun checkForUpdate(activity: Activity, manual: Boolean = false) {

        Thread {
            try {
                val url =
                    URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")

                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                connection.setRequestProperty("User-Agent", "GP-Schedule-App")
                connection.setRequestProperty("Accept", "application/vnd.github+json")

                val response =
                    connection.inputStream.bufferedReader().use { it.readText() }

                val release =
                    Gson().fromJson(response, GitHubRelease::class.java)

                val latestVersionName =
                    release.tag_name.removePrefix("v").trim()

                val currentVersionName =
                    activity.packageManager
                        .getPackageInfo(activity.packageName, 0)
                        .versionName
                        ?.trim() ?: "0.0.0"

                activity.runOnUiThread {
                    if (isNewerVersion(latestVersionName, currentVersionName)) {
                        showUpdateDialog(activity, release)
                    } else if (manual) {
                        Toast.makeText(
                            activity,
                            "You're already on the latest version",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Unable to check updates. Try again later.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }.start()
    }

    /**
     * Proper semantic version comparison.
     * Supports: 1.0.0, 1.10.2, 2.0, etc.
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {

        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }

            if (r > c) return true
            if (r < c) return false
        }

        return false
    }

    @SuppressLint("UseKtx")
    private fun showUpdateDialog(
        activity: Activity,
        release: GitHubRelease
    ) {

        val releasesPage =
            "https://github.com/$OWNER/$REPO/releases"

        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage(
                "Version ${release.tag_name} is available.\n\n"
            )
            .setPositiveButton("Open GitHub") { _, _ ->
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(releasesPage))
                )
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }
}