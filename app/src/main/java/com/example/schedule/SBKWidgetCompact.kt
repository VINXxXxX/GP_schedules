package com.example.schedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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

class SBKWidgetCompact : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach {
            updateAppWidget(context, appWidgetManager, it)
        }
    }

    companion object {
        private val compactCityMap = mapOf(

            // 4–5 chars (ultra safe)
            "Most" to "MOST",
            "Brno" to "BRNO",
            "Assen" to "ASSEN",
            "Jerez" to "JEREZ",

            // 6 chars (very safe)
            "Lusail" to "LUSAIL",
            "Motegi" to "MOTEGI",
            "Misano" to "MISANO",
            "Sepang" to "SEPANG",
            "Austin" to "AUSTIN",
            "Aragón" to "ARAGON",

            // 7 chars (safe)
            "Goiânia" to "GOIANIA",
            "Buriram" to "BURIRAM",
            "Balaton" to "BALATON",
            "Estoril" to "ESTORIL",
            "Mugello" to "MUGELLO",
            "Cremona" to "CREMONA",
            "Sachsenring" to "SACHSEN",
            "Mandalika" to "MANDALI",

            // 8 chars (OEM-safe upper limit)
            "Spielberg" to "SPIELBG",
            "Le Mans" to "LEMANS",
            "Portimão" to "PORTIMAO",
            "Valencia" to "VALENCIA",

            // Originally risky → safely compacted
            "Catalunya" to "CATALUN",
            "Silverstone" to "SILVERST",
            "Phillip Island" to "PHILLIP",
            "Donington" to "DONINGTN",

            // SBK specific
            "Magny-Cours" to "MAGNY"
        )


        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            val views = RemoteViews(
                context.packageName,
                R.layout.widget_sbk_compact
            )
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

            // ---------- TAP TO REFRESH ----------
            val refreshIntent = Intent(context, WidgetRefreshReceiver::class.java)
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent)

            try {
                // ---------- LOAD SBK RACES ----------
                val json = context.assets
                    .open("sbk_races.json")
                    .bufferedReader()
                    .use { it.readText() }

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

                // ---------- TODAY @ 00:00 ----------
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
                    selectedFriday = Calendar.getInstance().apply {
                        set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }

                val fridayCal = selectedFriday!!
                val saturdayCal = (fridayCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                val sundayCal = (fridayCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_MONTH, 2)
                }

                // ---------- ACTIVE DAY ----------
                val now = Calendar.getInstance()
                val activeDay = when {
                    now.before(fridayCal) -> Calendar.FRIDAY
                    now.before(saturdayCal) -> Calendar.FRIDAY
                    now.before(sundayCal) -> Calendar.SATURDAY
                    else -> Calendar.SUNDAY
                }

                // ---------- TITLE ----------
                val cityRaw = selectedRace.location
                    .split(",")
                    .first()
                    .trim()

                val city =
                    (compactCityMap[cityRaw] ?: cityRaw).uppercase().take(8)   // hard OEM-safe cap


                views.setTextViewText(
                    R.id.widget_title,
                    city
                )

                // ---------- DATE ----------
                views.setTextViewText(
                    R.id.widget_date,
                    RaceDateFormatter.formatWeekend(selectedFriday)
                )

                // ---------- COUNTDOWN / LIVE ----------
                val raceEnd =
                    fridayCal.timeInMillis + 3 * 24 * 60 * 60 * 1000

                val countdownText =
                    if (today.timeInMillis in fridayCal.timeInMillis until raceEnd) {
                        "LIVE"
                    } else {
                        val diffMillis = fridayCal.timeInMillis - today.timeInMillis
                        val days =
                            (diffMillis / (1000L * 60 * 60 * 24)).toInt()
                        "${if (days < 0) 0 else days}D"
                    }

                views.setTextViewText(R.id.widget_countdown, countdownText)
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)

                // ---------- BUILD SESSIONS ----------
                val fri = StringBuilder()
                val sat = StringBuilder()
                val sun = StringBuilder()

                val input = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                }
                val output = SimpleDateFormat("hh:mm a", Locale.getDefault())

                selectedRace.sessions.forEach { s ->
                    val time = try {
                        output.format(input.parse(s.sessionTime.trim())!!)
                    } catch (_: Exception) {
                        s.sessionTime
                    }

                    val raw = s.sessionName.lowercase()
                    val display = when {
                        raw.contains("spr") -> "SPR"
                        raw == "fp1" -> "FP1"
                        raw == "fp2" -> "PR"
                        raw == "fp3" -> "FP2"
                        raw.contains("race1") -> "R1"
                        raw.contains("race2") -> "R2"
                        raw.contains("sp") -> "SP"
                        else -> s.sessionName.uppercase()
                    }

                    val line = "$display $time\n"

                    when (raw) {
                        "fp1", "fp2" -> fri.append(line)
                        "fp3", "sp", "race1" -> sat.append(line)
                        "spr", "race2" -> sun.append(line)
                    }
                }


                // ---------- SHOW ONLY ACTIVE DAY ----------
                val isCompact = minWidth < 240

                if (isCompact) {
                    when (activeDay) {
                        Calendar.FRIDAY -> {
                            views.setTextViewText(
                                R.id.friday_sessions,
                                "FRI\n${fri.toString().trim()}"
                            )
                        }
                        Calendar.SATURDAY -> {
                            views.setTextViewText(
                                R.id.saturday_sessions,
                                "SAT\n${sat.toString().trim()}"
                            )
                        }
                        Calendar.SUNDAY -> {
                            views.setTextViewText(
                                R.id.sunday_sessions,
                                "SUN\n${sun.toString().trim()}"
                            )
                        }
                    }
                } else {
                    // non-compact → normal full columns
                    views.setTextViewText(R.id.friday_sessions, fri.toString().trim())
                    views.setTextViewText(R.id.saturday_sessions, sat.toString().trim())
                    views.setTextViewText(R.id.sunday_sessions, sun.toString().trim())
                }

                when (activeDay) {
                    Calendar.FRIDAY -> {
                        views.setViewVisibility(R.id.friday_sessions, View.VISIBLE)
                        views.setViewVisibility(R.id.saturday_sessions, View.GONE)
                        views.setViewVisibility(R.id.sunday_sessions, View.GONE)
                    }
                    Calendar.SATURDAY -> {
                        views.setViewVisibility(R.id.friday_sessions, View.GONE)
                        views.setViewVisibility(R.id.saturday_sessions, View.VISIBLE)
                        views.setViewVisibility(R.id.sunday_sessions, View.GONE)
                    }
                    Calendar.SUNDAY -> {
                        views.setViewVisibility(R.id.friday_sessions, View.GONE)
                        views.setViewVisibility(R.id.saturday_sessions, View.GONE)
                        views.setViewVisibility(R.id.sunday_sessions, View.VISIBLE)
                    }
                }

            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_title, "!")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
