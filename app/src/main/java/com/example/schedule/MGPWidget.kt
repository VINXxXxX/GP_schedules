package com.example.schedule

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.schedule.model.Race
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MGPWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_mgp)

            try {
                val jsonString = context.assets.open("motogp_races.json").bufferedReader().use { it.readText() }
                val gson = Gson()
                val mapType = object : TypeToken<Map<String, List<Race>>>() {}.type
                val jsonMap: Map<String, List<Race>> = gson.fromJson(jsonString, mapType)
                val races = jsonMap["races"] ?: emptyList()

                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())

                // Find initial upcoming race
                var upcomingRaces = races.filter { it.race >= todayStr }
                var race = upcomingRaces.firstOrNull() ?: races.first()

                // Calculate Friday (now the race field is Friday date)
                val raceCalendar = Calendar.getInstance().apply {
                    val parts = race.race.split("-")
                    set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                }
                val fridayCalendar = raceCalendar // Friday is the race date
                val mondayAfterCalendar = (raceCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 3) } // Monday after (Friday + 3 days)

                val todayCalendar = Calendar.getInstance()

// If today is Monday or later after the weekend → show next race
                if (todayCalendar.after(mondayAfterCalendar)) {
                    val nextRaces = races.filter { it.race > race.race }
                    race = nextRaces.firstOrNull() ?: races.first()

                    // Recalculate for next race
                    val nextRaceCalendar = Calendar.getInstance().apply {
                        val parts = race.race.split("-")
                        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    }
                    fridayCalendar.timeInMillis = nextRaceCalendar.timeInMillis
                    mondayAfterCalendar.timeInMillis = nextRaceCalendar.timeInMillis + 3 * 24 * 60 * 60 * 1000
                }

// Update title and date (date now shows Friday)
                val city = race.location.split(",").first().trim().uppercase()
                views.setTextViewText(R.id.widget_title, "${race.round} $city")

                val parts = race.race.split("-")
                val month = when (parts[1].toInt()) {
                    1 -> "JAN"; 2 -> "FEB"; 3 -> "MAR"; 4 -> "APR"; 5 -> "MAY"; 6 -> "JUN"
                    7 -> "JUL"; 8 -> "AUG"; 9 -> "SEP"; 10 -> "OCT"; 11 -> "NOV"; 12 -> "DEC"
                    else -> "???"
                }
                views.setTextViewText(R.id.widget_date, "${parts[2]} $month")

// Countdown logic (days only to Friday)
                val countdownText = when {
                    todayCalendar.after(mondayAfterCalendar) -> {
                        val diffMillis = fridayCalendar.timeInMillis - todayCalendar.timeInMillis
                        val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                        if (days > 1) "${days}D" else if (days == 1) "Tmrw" else "Soon"
                    }
                    todayCalendar.after(fridayCalendar) -> "LIVE"
                    else -> {
                        val diffMillis = fridayCalendar.timeInMillis - todayCalendar.timeInMillis
                        val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                        if (days > 1) "${days}D" else if (days == 1) "Tmrw" else "Today!"
                    }
                }
                views.setTextViewText(R.id.widget_countdown, countdownText)
                views.setTextViewText(R.id.widget_countdown, countdownText)

                // Sessions table
                val fri = StringBuilder("FRI\n")
                val sat = StringBuilder("SAT\n")
                val sun = StringBuilder("SUN\n")

                race.sessions.forEach { s ->
                    val name = when (s.sessionName.lowercase()) {
                        "fp1" -> "FP1"
                        "fp2" -> "FP2"
                        "fp3" -> "FP3"
                        "q1" -> "Q1"
                        "q2" -> "Q2"
                        "sprint" -> "SPR"
                        "race" -> "RACE"
                        else -> s.sessionName.uppercase()
                    }
                    val line = "$name ${s.sessionTime}\n"

                    when (s.sessionName.lowercase()) {
                        "fp1", "fp2" -> fri.append(line)
                        "fp3", "q1", "q2", "sprint" -> sat.append(line)
                        "race" -> sun.append(line)
                        else -> sun.append(line)
                    }
                }

                views.setTextViewText(R.id.friday_sessions, fri.toString().trim())
                views.setTextViewText(R.id.saturday_sessions, sat.toString().trim())
                views.setTextViewText(R.id.sunday_sessions, sun.toString().trim())

            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_title, "!")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}