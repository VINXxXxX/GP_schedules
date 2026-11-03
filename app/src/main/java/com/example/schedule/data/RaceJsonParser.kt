package com.example.schedule.data

import com.example.schedule.model.Race
import com.example.schedule.model.Session
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Thrown when a schedule file can't be used. The message is shown to the user,
 * so keep it short and point at the field that's wrong.
 */
class ScheduleFormatException(message: String) : Exception(message)

/**
 * Parses and validates a MotoGP schedule JSON file.
 *
 * Accepted shape (same as assets/motogp_races.json):
 *
 * {
 *   "races": [
 *     {
 *       "name": "Buriram Grand Prix",
 *       "location": "Buriram, Thailand",
 *       "track": "Chang International Circuit",
 *       "round": 1,
 *       "category": "MotoGP",
 *       "race": "2027-03-05",                       <- Friday of the weekend
 *       "sessions": [
 *         { "sessionName": "fp1", "sessionTime": "09:15 AM", "dayOffset": 0 }
 *       ]
 *     }
 *   ]
 * }
 *
 * Session times are IST (Asia/Kolkata). dayOffset counts days from "race".
 * Optional per session: "label" (display name) and "duration" (minutes).
 * Session names are normalised by [SessionCatalog.canonical] ("Sprint_Quali" → "sq").
 * A bare top-level array of races is also accepted. Entries whose category is
 * set to something other than MotoGP are ignored.
 *
 * Pure Kotlin + Gson (no Android classes) so it can be unit tested on the JVM.
 */
object RaceJsonParser {

    private val TIME_REGEX =
        Regex("""^(0?[1-9]|1[0-2]):([0-5]\d)\s*(AM|PM)$""", RegexOption.IGNORE_CASE)

    private val DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** Stored sessionTime for a session whose time isn't published yet */
    const val TBA = "TBA"
    private val TBA_VALUES = setOf("TBA", "TBC", "TBD")

    fun parse(json: String): List<Race> {
        val root: JsonElement = try {
            JsonParser.parseString(json.removePrefix("﻿"))
        } catch (e: Exception) {
            throw ScheduleFormatException("This file isn't valid JSON.")
        }

        val array: JsonArray = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject.get("races")?.isJsonArray == true ->
                root.asJsonObject.getAsJsonArray("races")
            else -> throw ScheduleFormatException(
                "Expected a JSON object with a \"races\" list."
            )
        }

        if (array.size() == 0) {
            throw ScheduleFormatException("The \"races\" list is empty.")
        }

        val races = array.mapIndexed { index, element -> parseRace(index + 1, element) }
            .filter { it.category.equals("MotoGP", ignoreCase = true) }

        if (races.isEmpty()) {
            throw ScheduleFormatException(
                "No MotoGP races found. Check the \"category\" field."
            )
        }

        return races.sortedWith(compareBy({ it.race }, { it.round }))
    }

    /** e.g. "2027 · 22 ROUNDS" */
    fun describe(races: List<Race>): String {
        val years = races.map { it.race.take(4) }.distinct()
        val season = if (years.size == 1) years[0] else "${years.first()}–${years.last()}"
        val rounds = if (races.size == 1) "1 ROUND" else "${races.size} ROUNDS"
        return "$season · $rounds"
    }

    // ------------------------------------------------------------------

    private fun parseRace(position: Int, element: JsonElement): Race {
        if (!element.isJsonObject) {
            throw ScheduleFormatException("Race #$position is not a JSON object.")
        }
        val obj = element.asJsonObject

        val name = obj.string("name")
            ?: throw ScheduleFormatException("Race #$position is missing \"name\".")
        val label = "Race #$position ($name)"

        val location = obj.string("location")
            ?: throw ScheduleFormatException("$label is missing \"location\".")

        val raceDate = obj.string("race")
            ?: throw ScheduleFormatException("$label is missing \"race\" (the Friday date).")
        if (!isValidDate(raceDate)) {
            throw ScheduleFormatException(
                "$label: \"race\" must be a real date like 2027-03-05, got \"$raceDate\"."
            )
        }

        val round = obj.int("round") ?: position
        val track = obj.string("track") ?: ""
        val category = obj.string("category") ?: "MotoGP"

        val sessionsJson = obj.get("sessions")
        if (sessionsJson == null || !sessionsJson.isJsonArray || sessionsJson.asJsonArray.size() == 0) {
            throw ScheduleFormatException("$label has no \"sessions\".")
        }

        val sessions = sessionsJson.asJsonArray.mapIndexed { i, s ->
            parseSession(label, i + 1, s)
        }

        val slug = obj.string("slug") ?: slugify(name)
        val localeKey = obj.string("localeKey") ?: slug

        return Race(
            name = name,
            location = location,
            track = track,
            round = round,
            category = category,
            race = raceDate,
            sessions = sessions,
            slug = slug,
            localeKey = localeKey
        )
    }

    private fun parseSession(raceLabel: String, position: Int, element: JsonElement): Session {
        if (!element.isJsonObject) {
            throw ScheduleFormatException("$raceLabel: session #$position is not a JSON object.")
        }
        val obj = element.asJsonObject

        val name = obj.string("sessionName")?.let { SessionCatalog.canonical(it) }
            ?: throw ScheduleFormatException("$raceLabel: session #$position is missing \"sessionName\".")

        val rawTime = obj.string("sessionTime")
            ?: throw ScheduleFormatException("$raceLabel: $name is missing \"sessionTime\".")

        // "TBA" / "TBC": the session is shown in the app but gets no alerts until a time is known
        val time = if (rawTime.trim().uppercase(Locale.ENGLISH) in TBA_VALUES) {
            TBA
        } else {
            val match = TIME_REGEX.matchEntire(rawTime)
                ?: throw ScheduleFormatException(
                    "$raceLabel: $name time must look like 09:15 AM (or TBA), got \"$rawTime\"."
                )

            // Normalise "9:15am" -> "09:15 AM" so every parser in the app accepts it
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2]
            val amPm = match.groupValues[3].uppercase(Locale.ENGLISH)
            String.format(Locale.ENGLISH, "%02d:%s %s", hour, minute, amPm)
        }

        val dayOffset = obj.int("dayOffset") ?: SessionCatalog.defaultDayOffset(name)
        if (dayOffset !in 0..6) {
            throw ScheduleFormatException("$raceLabel: $name has an odd \"dayOffset\" ($dayOffset).")
        }

        val label = obj.string("label")?.take(8)

        val duration = obj.int("duration")
        if (duration != null && duration !in 1..300) {
            throw ScheduleFormatException("$raceLabel: $name \"duration\" should be minutes (1–300).")
        }

        return Session(
            sessionName = name,
            sessionTime = time,
            dayOffset = dayOffset,
            label = label,
            duration = duration
        )
    }

    private fun isValidDate(value: String): Boolean {
        if (!DATE_REGEX.matches(value)) return false
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                .apply { isLenient = false }
                .parse(value) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun slugify(name: String): String =
        name.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun JsonObject.string(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        return el.asString.trim().takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.int(key: String): Int? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val p = el.asJsonPrimitive
        return when {
            p.isNumber -> p.asNumber.toInt()
            p.isString -> p.asString.trim().toIntOrNull()
            else -> null
        }
    }
}
