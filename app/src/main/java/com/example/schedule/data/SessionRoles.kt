package com.example.schedule.data

import com.example.schedule.model.Race
import com.example.schedule.model.Session

/**
 * One-line "what this session decides" notes, following the season's format.
 *
 * 2027: Fri FP1 · FP2 (practice only) · Sprint Qualifying (sets Sprint grid)
 *       Sat FP3 · Q1 (all riders, top 12 → Q2) · Q2 (sets GP grid) · Sprint
 *       Sun sighting laps (replaces warm-up) · Grand Prix
 */
object SessionRoles {

    fun note(race: Race, session: Session): String {
        val newFormat = race.season() >= 2027
        return when (SessionCatalog.canonical(session.sessionName)) {
            "fp1" -> "Practice"
            "fp2" -> if (newFormat) "Practice only · no Q2 places" else "Top 10 go straight to Q2"
            "sq" -> "Sets the Sprint grid"
            "fp3" -> "Practice"
            "q1" -> if (newFormat) "All riders · top 12 go to Q2" else "Fastest 2 join Q2"
            "q2" -> if (newFormat) "Sets the Grand Prix grid" else "Sets the grid"
            "sprint" -> "Half-distance race"
            "sighting" -> "Pit lane opens before the Grand Prix"
            "warmup" -> "Warm up"
            "race" -> "Main event"
            else -> ""
        }
    }

    /** Sprint and Grand Prix get extra weight in lists and widgets */
    fun isRace(session: Session): Boolean =
        SessionCatalog.canonical(session.sessionName) in setOf("sprint", "race")

    /** Sessions that set a grid get a tag on the session screen */
    fun gridTag(race: Race, session: Session): String? = when (SessionCatalog.canonical(session.sessionName)) {
        "sq" -> if (race.season() >= 2027) "SETS SPRINT GRID · NEW 2027" else "SETS SPRINT GRID"
        "q2" -> "SETS GP GRID"
        else -> null
    }
}
