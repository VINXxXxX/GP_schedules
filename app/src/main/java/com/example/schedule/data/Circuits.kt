package com.example.schedule.data

import android.content.Context
import android.util.Log
import com.example.schedule.model.Race
import com.google.gson.JsonParser
import java.util.Locale

/** Facts about one circuit, from assets/circuits.json */
data class Circuit(
    val id: String,
    val name: String,
    val match: List<String>,
    val lat: Double,
    val lon: Double,
    /** IANA time zone, e.g. "Asia/Bangkok" */
    val tz: String,
    val lengthKm: Double?,
    val left: Int?,
    val right: Int?,
    /** Total corners when the left/right split isn't published */
    val corners: Int?,
    val longestStraightM: Int?,
    val opened: Int?
) {
    val totalCorners: Int? get() = corners ?: if (left != null && right != null) left + right else null
}

/** Offline circuit data; a race is matched by words in its track, location or name. */
object Circuits {

    @Volatile private var cache: List<Circuit>? = null

    fun all(context: Context): List<Circuit> {
        cache?.let { return it }
        val list = try {
            val json = context.applicationContext.assets.open("circuits.json").bufferedReader().use { it.readText() }
            JsonParser.parseString(json).asJsonObject.getAsJsonArray("circuits").map { e ->
                val o = e.asJsonObject
                fun int(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asInt
                fun dbl(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.asDouble
                Circuit(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    match = o.getAsJsonArray("match").map { it.asString.lowercase(Locale.ROOT) },
                    lat = o.get("lat").asDouble,
                    lon = o.get("lon").asDouble,
                    tz = o.get("tz").asString,
                    lengthKm = dbl("lengthKm"),
                    left = int("left"),
                    right = int("right"),
                    corners = int("corners"),
                    longestStraightM = int("longestStraightM"),
                    opened = int("opened")
                )
            }
        } catch (e: Exception) {
            Log.e("Circuits", "circuits.json unreadable", e)
            emptyList()
        }
        cache = list
        return list
    }

    fun forRace(context: Context, race: Race): Circuit? {
        val hay = "${race.track} ${race.location} ${race.name}".lowercase(Locale.ROOT)
        return all(context).firstOrNull { c -> c.match.any { hay.contains(it) } }
    }
}
