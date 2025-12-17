package com.example.schedule

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.schedule.model.Race
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class SBKWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach {
            updateAppWidget(context, appWidgetManager, it)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            // ---------- WIDGET SIZE ----------
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight =
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            // Nothing OS / tight grids safety
            val isCompact = minWidth < 240

            Log.d(
                "SBKWidget",
                "id=$appWidgetId minW=$minWidth minH=$minHeight compact=$isCompact"
            )

            val views = RemoteViews(context.packageName, R.layout.widget_sbk)

            try {
                // ---------- LOAD RACES ----------
                val json = context.assets
                    .open("sbk_races.json")
                    .bufferedReader()
                    .use { it.readText() }

                val mapType = object : TypeToken<Map<String, List<Race>>>() {}.type
                val races = (Gson().fromJson<Map<String, List<Race>>>(json, mapType)["races"]
                    ?: emptyList())
                    .sortedBy { it.race }

                if (races.isEmpty()) {
                    views.setTextViewText(R.id.widget_title, "!")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return
                }

                // ---------- TODAY (00:00) ----------
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // ---------- SELECT RACE ----------
                var selectedRace = races.first()
                var selectedFriday: Calendar? = null

                for (race in races) {
                    val friday = Calendar.getInstance().apply {
                        val p = race.race.split("-")
                        set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val mondayAfter = (friday.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_MONTH, 3)
                    }

                    if (today.timeInMillis < friday.timeInMillis ||
                        today.timeInMillis in friday.timeInMillis until mondayAfter.timeInMillis
                    ) {
                        selectedRace = race
                        selectedFriday = friday
                        break
                    }
                }

                if (selectedFriday == null) {
                    val p = selectedRace.race.split("-")
                    selectedFriday = Calendar.getInstance().apply {
                        set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }

                // ---------- TITLE ----------
                val city = selectedRace.location
                    .split(",")
                    .first()
                    .trim()
                    .uppercase()

                views.setTextViewText(
                    R.id.widget_title,
                    "${selectedRace.round} $city"
                )

                val months = arrayOf(
                    "JAN","FEB","MAR","APR","MAY","JUN",
                    "JUL","AUG","SEP","OCT","NOV","DEC"
                )

                views.setTextViewText(
                    R.id.widget_date,
                    "${selectedFriday.get(Calendar.DAY_OF_MONTH)} ${
                        months[selectedFriday.get(Calendar.MONTH)]
                    }"
                )

                // ---------- COUNTDOWN / LIVE ----------
                val raceEnd =
                    selectedFriday.timeInMillis + 3 * 24 * 60 * 60 * 1000

                val countdownText =
                    if (today.timeInMillis in selectedFriday.timeInMillis until raceEnd) {
                        "LIVE"
                    } else {
                        val diffMillis =
                            selectedFriday.timeInMillis - today.timeInMillis
                        val days =
                            (diffMillis / (1000L * 60 * 60 * 24)).toInt()
                        "${if (days < 0) 0 else days}D"
                    }

                views.setTextViewText(R.id.widget_countdown, countdownText)

                // ---------- SESSION VISIBILITY ----------
                if (isCompact) {
                    views.setViewVisibility(R.id.friday_sessions, View.GONE)
                    views.setViewVisibility(R.id.saturday_sessions, View.GONE)
                    views.setViewVisibility(R.id.sunday_sessions, View.GONE)
                } else {
                    views.setViewVisibility(R.id.friday_sessions, View.VISIBLE)
                    views.setViewVisibility(R.id.saturday_sessions, View.VISIBLE)
                    views.setViewVisibility(R.id.sunday_sessions, View.VISIBLE)
                }

                // ---------- SESSIONS ----------
                val fri = StringBuilder("FRI\n")
                val sat = StringBuilder("SAT\n")
                val sun = StringBuilder("SUN\n")

                val input = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }

                val output = SimpleDateFormat("hh:mm a", Locale.getDefault())

                selectedRace.sessions.forEach { s ->
                    val name = when (s.sessionName.lowercase()) {
                        "fp1" -> "FP1"
                        "fp2" -> "FP2"
                        "fp3" -> "FP3"
                        "q1" -> "Q1"
                        "q2" -> "Q2"
                        "spr" -> "SPR"
                        "race1" -> "R1"
                        "race2" -> "R2"
                        "sp" -> "SP"
                        else -> s.sessionName.uppercase()
                    }

                    val time = try {
                        output.format(input.parse(s.sessionTime.trim())!!)
                    } catch (_: Exception) {
                        s.sessionTime
                    }

                    val line = "$name $time\n"

                    when (s.sessionName.lowercase()) {
                        "fp1", "fp2" -> fri.append(line)
                        "fp3", "q1", "q2", "sp", "race1" -> sat.append(line)
                        "spr", "race", "race2" -> sun.append(line)
                        else -> sun.append(line)
                    }
                }

                views.setTextViewText(
                    R.id.friday_sessions,
                    fri.toString().trim()
                )
                views.setTextViewText(
                    R.id.saturday_sessions,
                    sat.toString().trim()
                )
                views.setTextViewText(
                    R.id.sunday_sessions,
                    sun.toString().trim()
                )

            } catch (e: Exception) {
                Log.e("SBKWidget", "Widget update failed", e)
                views.setTextViewText(R.id.widget_title, "!")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
