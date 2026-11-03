package com.example.schedule.data

import android.content.Context
import android.util.Log
import com.example.schedule.model.Race
import com.example.schedule.model.Session
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Keeps session times right without editing any JSON by hand.
 *
 * Sources, in order:
 *   1. schedule/motogp_<year>.json in the GP_schedules GitHub repo — kept up to date by a
 *      GitHub Action (tools/sync_schedule.py) that reads MotoGP's own timetable every few hours
 *   2. MotoGP's timetable feed directly (api.motogp.pulselive.com), if GitHub can't be reached
 *   3. nothing → the bundled / imported times stay as they are
 *
 * Results are stored as small corrections ("SQ of R19 is now 2:20 PM, was TBA") and applied
 * on top of whatever season is loaded, so the bundled files and your own edits stay intact.
 */
object ScheduleSync {

    private const val TAG = "ScheduleSync"
    private const val OWNER = "VINXxXxX"
    private const val REPO = "GP_schedules"
    private const val BRANCH = "master"
    private const val API = "https://api.motogp.pulselive.com/motogp/v1"

    private const val PREFS = "schedule_sync"
    private const val KEY_FIXES = "fixes"
    private const val KEY_CHECKED = "checked_at"
    private const val KEY_SOURCE = "source"

    /** A change bigger than this is only taken for TBA / predicted sessions (guards against bad data) */
    private const val MAX_SHIFT_MS = 3 * 60 * 60 * 1000L

    /** Sessions whose built-in time is a guess, so any official time replaces it */
    private val PREDICTED = setOf("sq", "sighting")

    private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    data class Fix(val time: String, val dayOffset: Int, val was: String?, val wasOffset: Int?, val duration: Int?)

    enum class Source { GITHUB, MOTOGP, NONE }

    data class Result(val ok: Boolean, val changed: Boolean, val source: Source, val moved: Int)

    // ==================================================================
    // APPLY (cheap, offline — called by RaceRepository)
    // ==================================================================

    fun raceKey(race: Race) = "${race.season()}|R${race.round}"

    fun apply(context: Context, base: List<Race>): List<Race> {
        val fixes = load(context)
        if (fixes.isEmpty()) return base
        return base.map { race ->
            val f = fixes[raceKey(race)] ?: return@map race
            val names = race.sessions.map { SessionCatalog.canonical(it.sessionName) }.toSet()
            val updated = race.sessions.map { s ->
                f[SessionCatalog.canonical(s.sessionName)]?.let { s.copy(sessionTime = it.time, dayOffset = it.dayOffset) } ?: s
            }
            val added = f.filterKeys { it !in names }.map { (name, fx) ->
                Session(sessionName = name, sessionTime = fx.time, dayOffset = fx.dayOffset, duration = fx.duration)
            }
            race.copy(sessions = updated + added)
        }
    }

    /** Old start (epoch ms) of a session the sync moved, for "Moved · was 1:30 PM"; null if not moved */
    fun movedFrom(context: Context, race: Race, session: Session): Long? {
        val fx = load(context)[raceKey(race)]?.get(SessionCatalog.canonical(session.sessionName)) ?: return null
        val was = fx.was ?: return null
        if (was == RaceJsonParser.TBA) return null
        return WeekendTimeline.startMillis(race, session.copy(sessionTime = was, dayOffset = fx.wasOffset ?: session.dayOffset))
    }

    fun lastChecked(context: Context): Long = prefs(context).getLong(KEY_CHECKED, 0L)

    fun lastSource(context: Context): Source =
        runCatching { Source.valueOf(prefs(context).getString(KEY_SOURCE, "NONE")!!) }.getOrDefault(Source.NONE)

    // ==================================================================
    // SYNC (network — background thread only)
    // ==================================================================

    fun sync(context: Context): Result {
        val app = context.applicationContext
        val base = RaceRepository.baseRaces(app)
        val season = base.firstOrNull()?.season() ?: return Result(false, false, Source.NONE, 0)

        var source = Source.NONE
        var fixes: Map<String, Map<String, Fix>>? = null

        // 1. GitHub file kept current by the GitHub Action
        try {
            val json = get("https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/schedule/motogp_$season.json")
            val remote = RaceJsonParser.parse(json)
            fixes = diffFromRaces(base, remote, trusted = true)
            source = Source.GITHUB
        } catch (e: Exception) {
            Log.w(TAG, "GitHub schedule unavailable", e)
        }

        // 2. MotoGP's own feed
        if (fixes == null) {
            try {
                fixes = diffFromMotoGp(base, fetchEvents(season))
                source = Source.MOTOGP
            } catch (e: Exception) {
                Log.w(TAG, "MotoGP timetable unavailable", e)
            }
        }

        if (fixes == null) return Result(false, false, Source.NONE, 0)

        val before = load(app)
        // Keep corrections for other seasons, replace this season's
        val merged = before.filterKeys { !it.startsWith("$season|") } + fixes
        val changed = merged != before
        save(app, merged)
        prefs(app).edit()
            .putLong(KEY_CHECKED, System.currentTimeMillis())
            .putString(KEY_SOURCE, source.name)
            .apply()

        if (changed) RaceRepository.invalidate()
        return Result(true, changed, source, fixes.values.sumOf { it.size })
    }

    // ------------------------------------------------------------------
    // DIFFS
    // ------------------------------------------------------------------

    /** Corrections = what differs between our schedule and the remote one, per round + session */
    private fun diffFromRaces(base: List<Race>, remote: List<Race>, trusted: Boolean): Map<String, Map<String, Fix>> {
        val out = mutableMapOf<String, Map<String, Fix>>()
        base.forEach { race ->
            val other = remote.firstOrNull { it.round == race.round && it.season() == race.season() } ?: return@forEach
            val proposals = other.sessions.mapNotNull { rs ->
                // Remote dayOffset is relative to ITS Friday; re-base on ours
                val start = WeekendTimeline.startMillis(other, rs) ?: return@mapNotNull null
                SessionCatalog.canonical(rs.sessionName) to (start to rs.duration)
            }.toMap()
            fixesFor(race, proposals, trusted)?.let { out[raceKey(race)] = it }
        }
        return out
    }

    private fun diffFromMotoGp(base: List<Race>, events: JsonArray): Map<String, Map<String, Fix>> {
        val out = mutableMapOf<String, Map<String, Fix>>()
        val season = base.firstOrNull()?.season() ?: return out
        events.forEach { ev ->
            val e = ev.asObjOrNull() ?: return@forEach
            if (e.str("kind")?.uppercase(Locale.ROOT) == "TEST") return@forEach
            val sessions = e.get("broadcasts")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@forEach

            val proposals = mutableMapOf<String, Pair<Long, Int?>>()
            sessions.forEach { b ->
                val o = b.asObjOrNull() ?: return@forEach
                val cat = o.get("category")?.asObjOrNull()
                val isMgp = cat?.str("acronym")?.uppercase(Locale.ROOT) == "MGP" ||
                        cat?.str("name")?.uppercase(Locale.ROOT)?.startsWith("MOTOGP") == true
                if (!isMgp) return@forEach
                if (o.str("type")?.uppercase(Locale.ROOT)?.let { it != "SESSION" } == true) return@forEach
                val name = canonicalFromMotoGp(o.str("shortname"), o.str("name"), season) ?: return@forEach
                val start = parseTime(o.str("date_start")) ?: return@forEach
                proposals[name] = start to null
            }
            if (proposals.isEmpty()) return@forEach

            // The round whose weekend the sessions fall in
            val first = proposals.values.minOf { it.first }
            val race = base.minByOrNull { r -> Math.abs(fridayMillis(r) - first) }
                ?.takeIf { r -> Math.abs(fridayMillis(r) - first) <= 3 * 86_400_000L } ?: return@forEach
            fixesFor(race, proposals, trusted = false)?.let { out[raceKey(race)] = it }
        }
        return out
    }

    /** Turns proposed starts into corrections for one round, with sanity checks */
    private fun fixesFor(race: Race, proposals: Map<String, Pair<Long, Int?>>, trusted: Boolean): Map<String, Fix>? {
        val fixes = mutableMapOf<String, Fix>()
        proposals.forEach { (name, p) ->
            val (start, duration) = p
            val (time, offset) = toIst(race, start) ?: return@forEach   // outside the weekend → ignore
            val mine = race.sessions.firstOrNull { SessionCatalog.canonical(it.sessionName) == name }
            if (mine != null && mine.sessionTime == time && mine.dayOffset == offset) return@forEach

            val mineStart = mine?.let { WeekendTimeline.startMillis(race, it) }
            val guess = mine == null || mineStart == null || name in PREDICTED
            if (!trusted && !guess && Math.abs(start - mineStart!!) > MAX_SHIFT_MS) {
                Log.w(TAG, "R${race.round} $name: ${mine?.sessionTime} -> $time is a big jump, not applied")
                return@forEach
            }
            fixes[name] = Fix(time, offset, mine?.sessionTime, mine?.dayOffset, duration ?: mine?.duration)
        }
        return fixes.takeIf { it.isNotEmpty() }
    }

    /** MotoGP feed names → our session names (season-aware: 2026 "PR" is our fp2, 2027 "FP2" is fp2) */
    fun canonicalFromMotoGp(short: String?, name: String?, season: Int): String? {
        val s = short?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val n = name?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val newFormat = season >= 2027
        return when {
            s == "FP1" -> "fp1"
            s == "PR" -> "fp2"
            s == "FP2" -> if (newFormat) "fp2" else "fp3"
            s == "FP3" -> "fp3"
            s == "SQ" || n.contains("SPRINT QUAL") -> "sq"
            s == "Q1" -> "q1"
            s == "Q2" -> "q2"
            s == "SPR" || s == "SPRINT" || (n.contains("SPRINT") && !n.contains("QUAL")) -> "sprint"
            s == "WUP" || n.contains("WARM") -> "warmup"
            s.startsWith("SIGHT") || n.contains("SIGHTING") -> "sighting"
            s == "RAC" || s == "RACE" || n == "RACE" || n.contains("GRAND PRIX") -> "race"
            !newFormat && n == "PRACTICE" -> "fp2"
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    private fun fetchEvents(season: Int): JsonArray {
        val urls = listOf("$API/events?seasonYear=$season", "$API/events/seasonYear=$season")
        var last: Exception? = null
        urls.forEach { u ->
            try {
                val el = JsonParser.parseString(get(u))
                if (el.isJsonArray) return el.asJsonArray
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No events")
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "GPSchedule/2 (personal app)")
            setRequestProperty("Accept", "application/json")
            useCaches = false
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode} for $url")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** "2026-10-31T15:00:00+0800" or "+08:00" → epoch ms */
    private fun parseTime(raw: String?): Long? {
        val v = raw?.trim() ?: return null
        val fixed = Regex("""([+-]\d{2}):(\d{2})$""").replace(v) { "${it.groupValues[1]}${it.groupValues[2]}" }
            .replace(Regex("Z$"), "+0000")
        return listOf("yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZ").firstNotNullOfOrNull { f ->
            runCatching { SimpleDateFormat(f, Locale.ENGLISH).parse(fixed)?.time }.getOrNull()
        }
    }

    private fun fridayMillis(race: Race): Long {
        val p = race.race.split("-")
        return Calendar.getInstance(IST).apply { clear(); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0) }.timeInMillis
    }

    /** Instant → ("01:30 PM", dayOffset) in the JSON's IST convention; null outside the weekend */
    private fun toIst(race: Race, millis: Long): Pair<String, Int>? {
        val p = race.race.split("-")
        val friday = Calendar.getInstance(IST).apply { clear(); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()) }.timeInMillis
        val day = Calendar.getInstance(IST).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val offset = Math.round((day - friday) / 86_400_000.0).toInt()
        if (offset !in 0..6) return null
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply { timeZone = IST }.format(millis).uppercase(Locale.ENGLISH)
        return time to offset
    }

    // ------------------------------------------------------------------
    // STORAGE (one small JSON in prefs)
    // ------------------------------------------------------------------

    @Volatile private var memo: Map<String, Map<String, Fix>>? = null

    private fun load(context: Context): Map<String, Map<String, Fix>> {
        memo?.let { return it }
        val raw = prefs(context).getString(KEY_FIXES, null) ?: return emptyMap<String, Map<String, Fix>>().also { memo = it }
        val out = try {
            JsonParser.parseString(raw).asJsonObject.entrySet().associate { (race, sessions) ->
                race to sessions.asJsonObject.entrySet().associate { (name, f) ->
                    val o = f.asJsonObject
                    name to Fix(
                        time = o.get("t").asString,
                        dayOffset = o.get("d").asInt,
                        was = o.get("w")?.takeIf { !it.isJsonNull }?.asString,
                        wasOffset = o.get("wd")?.takeIf { !it.isJsonNull }?.asInt,
                        duration = o.get("m")?.takeIf { !it.isJsonNull }?.asInt
                    )
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
        memo = out
        return out
    }

    private fun save(context: Context, fixes: Map<String, Map<String, Fix>>) {
        val root = JsonObject()
        fixes.forEach { (race, sessions) ->
            val r = JsonObject()
            sessions.forEach { (name, f) ->
                r.add(name, JsonObject().apply {
                    addProperty("t", f.time)
                    addProperty("d", f.dayOffset)
                    f.was?.let { addProperty("w", it) }
                    f.wasOffset?.let { addProperty("wd", it) }
                    f.duration?.let { addProperty("m", it) }
                })
            }
            root.add(race, r)
        }
        prefs(context).edit().putString(KEY_FIXES, root.toString()).apply()
        memo = fixes
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun JsonElement.asObjOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive }?.asString
}
