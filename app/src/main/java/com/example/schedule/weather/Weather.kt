package com.example.schedule.weather

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.schedule.R
import com.example.schedule.data.Circuits
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.model.Race
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArraySet

/** One day's forecast at the circuit (track-local date). */
data class DayWeather(
    val date: String,      // yyyy-MM-dd, track local
    val code: Int,         // WMO weather code
    val tMax: Int,
    val tMin: Int,
    val rainChance: Int?   // %
) {
    val icon: Int get() = Weather.icon(code)
    val label: String get() = Weather.label(code)
}

/**
 * Race-week weather from Open-Meteo (free, no key). The only online feature:
 * fetched at most once a day, only from the Monday of race week to the end of the
 * weekend, cached so the widget and screens work offline after that.
 */
object Weather {

    private const val TAG = "Weather"
    private const val PREFS = "weather_cache"

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    // ------------------------------------------------------------------
    // WHEN
    // ------------------------------------------------------------------

    /** Monday 00:00 (phone time) of the race week → end of the weekend */
    fun inRaceWeek(race: Race, now: Long): Boolean {
        val sessions = WeekendTimeline.build(race)
        val start = sessions.minOfOrNull { it.start } ?: return false
        val end = WeekendTimeline.weekendEnd(race, sessions)
        val monday = Calendar.getInstance().apply {
            timeInMillis = start
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
        }.timeInMillis
        return now in monday until end
    }

    // ------------------------------------------------------------------
    // READ (offline, from cache)
    // ------------------------------------------------------------------

    /** Weekend days at the track: the Friday in the JSON + as many days as the sessions span. */
    fun weekendDates(race: Race): List<String> {
        val p = race.race.split("-")
        val days = (race.sessions.maxOfOrNull { it.dayOffset } ?: 2).coerceIn(2, 4)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        return (0..days).map { off ->
            val c = Calendar.getInstance().apply {
                clear(); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()); add(Calendar.DAY_OF_MONTH, off)
            }
            fmt.format(c.time)
        }
    }

    fun cached(context: Context, race: Race): Map<String, DayWeather> {
        val circuit = Circuits.forRace(context, race) ?: return emptyMap()
        val json = prefs(context).getString("json_${circuit.id}", null) ?: return emptyMap()
        return try { parse(json) } catch (_: Exception) { emptyMap() }
    }

    /** Forecast for the weekend days only, in order (may be shorter if not cached yet) */
    fun weekend(context: Context, race: Race): List<DayWeather> {
        val all = cached(context, race)
        return weekendDates(race).mapNotNull { all[it] }
    }

    /**
     * The one to show on the widget / home: today at the track during the weekend,
     * otherwise the Friday. Null outside race week or with nothing cached.
     */
    fun headline(context: Context, race: Race, now: Long): DayWeather? {
        if (!inRaceWeek(race, now)) return null
        val all = cached(context, race)
        if (all.isEmpty()) return null
        val circuit = Circuits.forRace(context, race) ?: return null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone(circuit.tz) }.format(now)
        val dates = weekendDates(race)
        return if (today in dates) all[today] else all[dates.first()]
    }

    // ------------------------------------------------------------------
    // FETCH (background thread only)
    // ------------------------------------------------------------------

    fun needsRefresh(context: Context, race: Race, now: Long): Boolean {
        val circuit = Circuits.forRace(context, race) ?: return false
        val day = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(now)
        return prefs(context).getString("day_${circuit.id}", null) != day
    }

    fun fetch(context: Context, race: Race, now: Long): Boolean {
        val circuit = Circuits.forRace(context, race) ?: return false
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${circuit.lat}&longitude=${circuit.lon}" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=16"
        )
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "GPSchedule/2 (personal app)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            parse(body) // validate before saving
            prefs(context).edit()
                .putString("json_${circuit.id}", body)
                .putString("day_${circuit.id}", SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(now))
                .apply()
            main.post { listeners.forEach { it() } }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed for ${circuit.id}", e)
            false
        }
    }

    private fun parse(json: String): Map<String, DayWeather> {
        val daily = JsonParser.parseString(json).asJsonObject.getAsJsonObject("daily")
        val dates = daily.getAsJsonArray("time")
        val codes = daily.getAsJsonArray("weather_code")
        val max = daily.getAsJsonArray("temperature_2m_max")
        val min = daily.getAsJsonArray("temperature_2m_min")
        val rain = daily.getAsJsonArray("precipitation_probability_max")
        val out = LinkedHashMap<String, DayWeather>()
        for (i in 0 until dates.size()) {
            if (codes[i].isJsonNull || max[i].isJsonNull || min[i].isJsonNull) continue
            out[dates[i].asString] = DayWeather(
                date = dates[i].asString,
                code = codes[i].asInt,
                tMax = Math.round(max[i].asDouble).toInt(),
                tMin = Math.round(min[i].asDouble).toInt(),
                rainChance = rain?.get(i)?.takeIf { !it.isJsonNull }?.asInt
            )
        }
        return out
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // WMO CODES
    // ------------------------------------------------------------------

    fun icon(code: Int): Int = when (code) {
        0 -> R.drawable.ic_wx_sun
        1, 2 -> R.drawable.ic_wx_partly
        3 -> R.drawable.ic_wx_cloud
        45, 48 -> R.drawable.ic_wx_fog
        in 51..67, in 80..82 -> R.drawable.ic_wx_rain
        in 71..77, 85, 86 -> R.drawable.ic_wx_snow
        in 95..99 -> R.drawable.ic_wx_storm
        else -> R.drawable.ic_wx_cloud
    }

    fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 80..82 -> "Showers"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}
