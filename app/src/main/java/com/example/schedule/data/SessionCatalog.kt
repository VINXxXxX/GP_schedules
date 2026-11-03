package com.example.schedule.data

import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.util.Locale

/**
 * Everything the app knows about session names, in one place.
 *
 * 2027 format (Friday FP1, FP2, Sprint Qualifying · Saturday FP3, Q1, Q2, Sprint · Sunday GP)
 * vs. the 2023–2026 format (Friday FP1, Practice · Saturday FP2, Q1, Q2, Sprint · Sunday GP).
 *
 * In the JSON the sessions keep their running order names: fp1, fp2, fp3, sq, q1, q2,
 * sprint, race. Labels then follow the season, so "fp2" reads PR before 2027 and FP2 from 2027.
 * A session the app doesn't know yet still works — it shows its own name and a 45 min default,
 * and a JSON "label" / "duration" can override anything.
 */
object SessionCatalog {

    data class Info(
        /** Widget / card label, ≤ 5 chars: "SQ", "FP3", "RACE" */
        val short: String,
        /** Hero label: "SPRINT QUALI" */
        val long: String,
        /** Typical length, used to know when a session is over */
        val minutes: Int
    )

    private const val NEW_FORMAT_FROM = 2027

    private val aliases = mapOf(
        "sq" to "sq",
        "sprintq" to "sq",
        "sprint_q" to "sq",
        "sprint_quali" to "sq",
        "sprint_qualifying" to "sq",
        "sprintqualifying" to "sq",
        "sprint-qualifying" to "sq",
        "sprint qualifying" to "sq",
        "qualifying1" to "q1",
        "qp1" to "q1",
        "qualifying2" to "q2",
        "qp2" to "q2",
        "spr" to "sprint",
        "sprint race" to "sprint",
        "tissot sprint" to "sprint",
        "gp" to "race",
        "grand prix" to "race",
        "grand_prix" to "race",
        "grandprix" to "race",
        "wup" to "warmup",
        "warm up" to "warmup",
        "warm_up" to "warmup",
        "sighting" to "sighting",
        "sighting laps" to "sighting"
    )

    /** Lower-cases and maps known aliases to one name ("Sprint_Quali" → "sq"). */
    fun canonical(name: String): String {
        val n = name.trim().lowercase(Locale.ENGLISH)
        return aliases[n] ?: n
    }

    fun info(sessionName: String, season: Int): Info {
        val newFormat = season >= NEW_FORMAT_FROM
        return when (canonical(sessionName)) {
            "fp1" -> Info("FP1", "FREE PRACTICE 1", 45)
            "fp2" ->
                if (newFormat) Info("FP2", "FREE PRACTICE 2", 30)
                else Info("PR", "PRACTICE", 60)
            "fp3" ->
                if (newFormat) Info("FP3", "FREE PRACTICE 3", 20)
                else Info("FP2", "FREE PRACTICE 2", 30)
            "sq" -> Info("SQ", "SPRINT QUALI", 15)
            "q1" -> Info("Q1", "QUALIFYING 1", 15)
            "q2" -> Info("Q2", "QUALIFYING 2", 15)
            "sprint" -> Info("SPR", "SPRINT", 30)
            "race" -> Info("RACE", "GRAND PRIX", 50)
            "warmup" -> Info("WUP", "WARM UP", 10)
            "sighting" -> Info("SIGHT", "SIGHTING LAPS", 10)
            else -> {
                val up = sessionName.trim().uppercase(Locale.ENGLISH)
                Info(up.take(5), up, 45)
            }
        }
    }

    /** Session's day if the JSON leaves dayOffset out (0 = Friday). */
    fun defaultDayOffset(sessionName: String): Int = when (canonical(sessionName)) {
        "fp1", "fp2", "sq" -> 0
        "fp3", "q1", "q2", "sprint" -> 1
        else -> 2
    }
}

/** Season year from the race date ("2027-03-05" → 2027). */
fun Race.season(): Int = race.take(4).toIntOrNull() ?: 0

fun Session.shortLabel(race: Race): String =
    label?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ENGLISH)
        ?: SessionCatalog.info(sessionName, race.season()).short

fun Session.longLabel(race: Race): String =
    label?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ENGLISH)
        ?: SessionCatalog.info(sessionName, race.season()).long

fun Session.durationMinutes(race: Race): Int =
    duration?.takeIf { it > 0 } ?: SessionCatalog.info(sessionName, race.season()).minutes
