package com.example.schedule

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.schedule.data.RaceJsonParser
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.ScheduleFormatException
import com.example.schedule.model.Race

/**
 * UI flow for loading a new season from a JSON file:
 * read + validate (background) → confirm → save → refresh widgets & notifications.
 *
 * Used by the Settings button and by "Open with GP Schedule" from a file manager.
 */
object ScheduleImporter {

    private const val TAG = "ScheduleImporter"

    fun importFromUri(activity: Activity, uri: Uri, onDone: (() -> Unit)? = null) {
        val app = activity.applicationContext

        Thread {
            val result = runCatching { RaceRepository.readFromUri(app, uri) }

            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                result
                    .onSuccess { races -> confirmImport(activity, races, onDone) }
                    .onFailure { showError(activity, it) }
            }
        }.start()
    }

    fun confirmReset(activity: Activity, onDone: (() -> Unit)? = null) {
        AlertDialog.Builder(activity)
            .setTitle("Use built-in schedule?")
            .setMessage("This removes the imported schedule and goes back to the one that shipped with the app.")
            .setPositiveButton("Reset") { _, _ ->
                val app = activity.applicationContext
                Thread {
                    RaceRepository.resetToBundled(app)
                    refreshEverything(app)
                    activity.runOnUiThread {
                        Toast.makeText(app, "Using built-in schedule", Toast.LENGTH_SHORT).show()
                        onDone?.invoke()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------

    private fun confirmImport(activity: Activity, races: List<Race>, onDone: (() -> Unit)?) {
        val first = races.first()
        val last = races.last()

        val message =
            "${RaceJsonParser.describe(races)}\n\n" +
                    "First: ${first.name} (${first.race})\n" +
                    "Last: ${last.name} (${last.race})\n\n" +
                    "This replaces the current schedule. Widgets and notifications update straight away."

        AlertDialog.Builder(activity)
            .setTitle("Import this schedule?")
            .setMessage(message)
            .setPositiveButton("Import") { _, _ -> save(activity, races, onDone) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(activity: Activity, races: List<Race>, onDone: (() -> Unit)?) {
        val app = activity.applicationContext

        Thread {
            val saved = runCatching { RaceRepository.saveImported(app, races) }
            if (saved.isSuccess) refreshEverything(app)

            activity.runOnUiThread {
                saved
                    .onSuccess {
                        Toast.makeText(
                            app,
                            "Schedule imported: ${RaceJsonParser.describe(races)}",
                            Toast.LENGTH_LONG
                        ).show()
                        onDone?.invoke()
                    }
                    .onFailure { showError(activity, it) }
            }
        }.start()
    }

    private fun refreshEverything(context: android.content.Context) {
        try {
            SchedulerCoordinator.onScheduleChanged(context)
        } catch (e: Exception) {
            Log.e(TAG, "Refresh after schedule change failed", e)
        }
    }

    private fun showError(activity: Activity, error: Throwable) {
        if (activity.isFinishing || activity.isDestroyed) return
        Log.w(TAG, "Import failed", error)

        val reason = (error as? ScheduleFormatException)?.message
            ?: "Something went wrong reading that file."

        AlertDialog.Builder(activity)
            .setTitle("Couldn't import schedule")
            .setMessage("$reason\n\nYour current schedule hasn't changed.")
            .setPositiveButton("OK", null)
            .show()
    }
}
