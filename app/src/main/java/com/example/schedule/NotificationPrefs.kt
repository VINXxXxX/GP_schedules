package com.example.schedule

import android.content.Context
import com.example.schedule.data.SessionCatalog
import com.example.schedule.model.Race
import com.example.schedule.model.Session

/**
 * Everything the user can choose about alerts.
 *
 *  • master switch
 *  • how long before a session (5 / 15 / 30 / 60 min)
 *  • which session types ("Quali + races" by default)
 *  • per-session overrides from the bell on the session screen
 *  • weekend preview, live session card, night-session behaviour
 */
object NotificationPrefs {

    private const val PREFS = "notification_prefs"
    private const val KEY_MOTOGP = "notify_motogp"
    private const val KEY_LEAD = "lead_minutes"
    private const val KEY_TYPES = "session_types"
    private const val KEY_FORCED_ON = "forced_on"
    private const val KEY_FORCED_OFF = "forced_off"
    private const val KEY_PREVIEW = "weekend_preview"
    private const val KEY_LIVE = "live_card"
    private const val KEY_NIGHT = "night_mode"
    private const val KEY_DAY_CARD = "race_day_card"
    private const val KEY_DAILY = "daily_schedule"

    enum class Night { RING, SILENT, SKIP }

    /** Session types shown as chips in Settings, in weekend order */
    val TYPES = listOf("fp1", "fp2", "sq", "fp3", "q1", "q2", "sprint", "race")

    val LEAD_OPTIONS = listOf(5, 15, 30, 60)

    val PRESET_ALL = TYPES.toSet()
    val PRESET_QUALI_RACES = setOf("sq", "q1", "q2", "sprint", "race")
    val PRESET_RACES = setOf("sprint", "race")

    /** Night window, local time: 00:00 – 06:30 */
    const val NIGHT_END_MINUTES = 6 * 60 + 30

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- master ----------------

    fun isMotoGPEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_MOTOGP, true)

    fun setMotoGP(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_MOTOGP, enabled).apply()

    // ---------------- lead time ----------------

    fun leadMinutes(context: Context): Int = prefs(context).getInt(KEY_LEAD, 15)

    fun setLeadMinutes(context: Context, minutes: Int) =
        prefs(context).edit().putInt(KEY_LEAD, minutes).apply()

    // ---------------- session types ----------------

    fun types(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_TYPES, null)?.toSet() ?: PRESET_QUALI_RACES

    fun setTypes(context: Context, types: Set<String>) =
        prefs(context).edit().putStringSet(KEY_TYPES, types.toSet()).apply()

    // ---------------- per-session bell ----------------

    fun key(race: Race, session: Session) =
        "${race.race}|${race.slug}|${SessionCatalog.canonical(session.sessionName)}"

    /** Is an alert set for this exact session? */
    fun isSessionOn(context: Context, race: Race, session: Session): Boolean {
        val k = key(race, session)
        val p = prefs(context)
        if (p.getStringSet(KEY_FORCED_ON, emptySet())!!.contains(k)) return true
        if (p.getStringSet(KEY_FORCED_OFF, emptySet())!!.contains(k)) return false
        return SessionCatalog.canonical(session.sessionName) in types(context)
    }

    fun setSessionOn(context: Context, race: Race, session: Session, on: Boolean) {
        val k = key(race, session)
        val p = prefs(context)
        val forcedOn = p.getStringSet(KEY_FORCED_ON, emptySet())!!.toMutableSet()
        val forcedOff = p.getStringSet(KEY_FORCED_OFF, emptySet())!!.toMutableSet()
        forcedOn.remove(k)
        forcedOff.remove(k)
        val byType = SessionCatalog.canonical(session.sessionName) in types(context)
        if (on != byType) (if (on) forcedOn else forcedOff).add(k)
        p.edit().putStringSet(KEY_FORCED_ON, forcedOn).putStringSet(KEY_FORCED_OFF, forcedOff).apply()
    }

    /** Choosing a preset or type in Settings clears the single-session overrides */
    fun clearOverrides(context: Context) =
        prefs(context).edit().remove(KEY_FORCED_ON).remove(KEY_FORCED_OFF).apply()

    // ---------------- extras ----------------

    /** Each weekend day, in the morning: that day's sessions */
    fun dailySchedule(context: Context) = prefs(context).getBoolean(KEY_DAILY, true)
    fun setDailySchedule(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_DAILY, on).apply()

    /** Monday of race week: the whole weekend */
    fun weekendPreview(context: Context) = prefs(context).getBoolean(KEY_PREVIEW, true)
    fun setWeekendPreview(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_PREVIEW, on).apply()

    fun liveCard(context: Context) = prefs(context).getBoolean(KEY_LIVE, true)
    fun setLiveCard(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_LIVE, on).apply()

    /** Lock-screen card counting down to the next session, on each weekend day */
    fun dayCard(context: Context) = prefs(context).getBoolean(KEY_DAY_CARD, true)
    fun setDayCard(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_DAY_CARD, on).apply()

    fun night(context: Context): Night =
        runCatching { Night.valueOf(prefs(context).getString(KEY_NIGHT, Night.SILENT.name)!!) }
            .getOrDefault(Night.SILENT)

    fun setNight(context: Context, night: Night) =
        prefs(context).edit().putString(KEY_NIGHT, night.name).apply()

    /** Chip / summary label, 2027 names */
    fun typeLabel(type: String): String = when (type) {
        "fp1" -> "FP1"
        "fp2" -> "FP2"
        "sq" -> "SQ"
        "fp3" -> "FP3"
        "q1" -> "Q1"
        "q2" -> "Q2"
        "sprint" -> "Sprint"
        "race" -> "Grand Prix"
        else -> type.uppercase()
    }
}
