package com.example.schedule

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.example.schedule.model.Race
import com.example.schedule.utils.RaceDateFormatter
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
        appWidgetIds.forEach { updateAppWidget(context, appWidgetManager, it) }
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

        @SuppressLint("UseKtx")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            // ---------- WIDGET SIZE ----------
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

            val isCompact = minWidth < 240

            // ---------- FONT SCALE (Nothing OS fix) ----------
            val fontScale = context.resources.configuration.fontScale
            val fontCompensation = if (fontScale > 1.0f) (1.0f / fontScale) else 1.0f

            Log.d(
                "SBKWidget",
                "id=$appWidgetId minW=$minWidth compact=$isCompact fontScale=$fontScale"
            )

            val views = RemoteViews(context.packageName, R.layout.widget_sbk)

            // ---------- MANUAL REFRESH (TAP) ----------
            val refreshIntent = Intent(context, WidgetRefreshReceiver::class.java)

            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // ---------- LOAD RACES ----------
                val json =
                    context.assets.open("sbk_races.json").bufferedReader().use { it.readText() }

                val mapType = object : TypeToken<Map<String, List<Race>>>() {}.type
                val races =
                    (Gson().fromJson<Map<String, List<Race>>>(json, mapType)["races"]
                        ?: emptyList())
                        .sortedBy { it.race }

                if (races.isEmpty()) {
                    views.setTextViewText(R.id.widget_title, "!")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return
                }

                // ---------- TODAY (00:00) ----------
                val today =
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                // ---------- SELECT RACE ----------
                var selectedRace = races.first()
                var selectedFriday: Calendar? = null

                for (race in races) {
                    val friday =
                        Calendar.getInstance().apply {
                            val p = race.race.split("-")
                            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                    val mondayAfter =
                        (friday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 3) }

                    if (
                        today.timeInMillis < friday.timeInMillis ||
                        today.timeInMillis in friday.timeInMillis until mondayAfter.timeInMillis
                    ) {
                        selectedRace = race
                        selectedFriday = friday
                        break
                    }
                }

                if (selectedFriday == null) {
                    val p = selectedRace.race.split("-")
                    selectedFriday =
                        Calendar.getInstance().apply {
                            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                }

                // ---------- TITLE ----------
                val parts = selectedRace.location.split(",")

                val city = parts.getOrNull(0)
                    ?.trim()
                    ?.uppercase()
                    ?: ""

                val country = parts.getOrNull(1)
                    ?.trim()
                    ?.uppercase()
                    ?: ""
                views.setTextViewText(
                    R.id.widget_title,
                    "${selectedRace.round} $city"
                )

                views.setTextViewText(
                    R.id.widget_country,
                    country
                )

                views.setTextViewText(
                    R.id.widget_date,
                    RaceDateFormatter.formatWeekend(selectedFriday)
                )

                // ---------- COUNTDOWN / LIVE ----------
                val raceEnd = selectedFriday.timeInMillis + 3 * 24 * 60 * 60 * 1000

                val countdownText =
                    if (today.timeInMillis in selectedFriday.timeInMillis until raceEnd) {
                        "LIVE"
                    } else {
                        val days =
                            ((selectedFriday.timeInMillis - today.timeInMillis) /
                                    (1000L * 60 * 60 * 24))
                                .toInt()
                        "${if (days < 0) 0 else days}D"
                    }

                views.setTextViewText(R.id.widget_countdown, countdownText)

                // ---------- APPLY TEXT SIZE FIX ----------
                val titleSize = if (isCompact) 24f else 27f
                val dateSize = if (isCompact) 18f else 20f
                val sessionSize = if (isCompact) 14.5f else 15.5f
                views.setFloat(R.id.widget_title, "setTextSize", titleSize * fontCompensation)
                views.setFloat(R.id.widget_date, "setTextSize", dateSize * fontCompensation)
                views.setFloat(R.id.friday_sessions, "setTextSize", sessionSize * fontCompensation)
                views.setFloat(
                    R.id.saturday_sessions,
                    "setTextSize",
                    sessionSize * fontCompensation
                )
                views.setFloat(R.id.sunday_sessions, "setTextSize", sessionSize * fontCompensation)

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

                val input =
                    SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                    }
                val output = SimpleDateFormat("hh:mm a", Locale.getDefault())

                selectedRace.sessions.forEach { s ->
                    val time =
                        try {
                            output.format(input.parse(s.sessionTime.trim())!!)
                        } catch (_: Exception) {
                            s.sessionTime
                        }

                    val raw = s.sessionName.lowercase()

                    val displayName =
                        when {
                            raw.contains("spr") -> "SPR"
                            raw == "fp1" -> "FP1"
                            raw == "fp2" -> "FP2"
                            raw == "fp3" -> "FP3"
                            raw == "race1" -> "R1"
                            raw == "race2" -> "R2"
                            raw.contains("sp") -> "SP"
                            else -> s.sessionName.uppercase()
                        }

                    val line = "$displayName $time\n"

                    when (s.sessionName.lowercase()) {
                        "fp1",
                        "fp2" -> fri.append(line)
                        "fp3",
                        "q1",
                        "q2",
                        "sp",
                        "race1" -> sat.append(line)
                        else -> sun.append(line)
                    }
                }

                views.setTextViewText(R.id.friday_sessions, fri.toString().trim())
                views.setTextViewText(R.id.saturday_sessions, sat.toString().trim())
                views.setTextViewText(R.id.sunday_sessions, sun.toString().trim())

                // ---------- LIVE / DIM DAY LOGIC (SBK) ----------

                val normalColor = android.graphics.Color.WHITE
                val dimColor = android.graphics.Color.parseColor("#66FFFFFF")
                val liveColor = android.graphics.Color.parseColor("#FF9494")

                // Reset colors (important to avoid stale state)
                views.setTextColor(R.id.friday_sessions, normalColor)
                views.setTextColor(R.id.saturday_sessions, normalColor)
                views.setTextColor(R.id.sunday_sessions, normalColor)

                // Build race day calendars
                val fridayCal = selectedFriday!!
                val saturdayCal =
                    (fridayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val sundayCal =
                    (fridayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 2) }

                val now = Calendar.getInstance()

                // ---------- DIM PAST DAYS ----------
                if (now.after(fridayCal)) {
                    views.setTextColor(R.id.friday_sessions, dimColor)
                }
                if (now.after(saturdayCal)) {
                    views.setTextColor(R.id.saturday_sessions, dimColor)
                }
                if (now.after(sundayCal)) {
                    views.setTextColor(R.id.sunday_sessions, dimColor)
                }

                // ---------- LIVE DAY HIGHLIGHT ----------
                val isLiveWeekend = today.timeInMillis in selectedFriday.timeInMillis until raceEnd

                if (isLiveWeekend) {
                    when (now.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.FRIDAY -> views.setTextColor(R.id.friday_sessions, liveColor)
                        Calendar.SATURDAY -> views.setTextColor(R.id.saturday_sessions, liveColor)
                        Calendar.SUNDAY -> views.setTextColor(R.id.sunday_sessions, liveColor)
                    }
                }
            } catch (e: Exception) {
                Log.e("SBKWidget", "Widget update failed", e)
                views.setTextViewText(R.id.widget_title, "!")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
