package com.example.schedule.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.schedule.model.Race
import com.google.gson.GsonBuilder
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Single source of race data for the whole app (list, widgets, notifications).
 *
 * Seasons come from:
 *   • the schedules bundled in assets/motogp_<year>.json (2026 and 2027 ship with the app)
 *   • a schedule the user imported / edited (app-private storage) — wins for its season
 * The app shows the first season that still has a weekend to come, so it moves on to
 * 2027 by itself once Valencia 2026 is over.
 *
 * On top of that, [ScheduleSync] corrections (official session times) are applied.
 * Importing a new season never needs an app rebuild.
 */
object RaceRepository {

    private const val TAG = "RaceRepository"
    private val ASSET_PATTERN = Regex("""^motogp_(\d{4})\.json$""")
    private const val IMPORT_DIR = "schedule"
    private const val IMPORT_FILE = "motogp_races.json"
    private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024 // 2 MB is far more than a season needs

    enum class Source { BUNDLED, IMPORTED }

    @Volatile
    private var cache: List<Race>? = null

    /** Day the cache was built (season choice depends on today) */
    @Volatile
    private var cacheDay: Long = -1L

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------
    // READ
    // ------------------------------------------------------------------

    /** Races sorted by date, official corrections applied. Never throws. */
    fun getRaces(context: Context): List<Race> {
        val today = System.currentTimeMillis() / 86_400_000L
        if (cacheDay == today) cache?.let { return it }
        synchronized(this) {
            if (cacheDay == today) cache?.let { return it }
            val app = context.applicationContext
            val races = ScheduleSync.apply(app, baseRaces(app))
            cache = races
            cacheDay = today
            return races
        }
    }

    /** The active season exactly as bundled / imported, without sync corrections. */
    fun baseRaces(context: Context): List<Race> {
        val app = context.applicationContext
        val seasons = linkedMapOf<Int, List<Race>>()
        loadBundledSeasons(app).forEach { (year, races) -> seasons[year] = races }
        loadImported(app)?.let { imported ->
            imported.groupBy { it.season() }.forEach { (year, races) -> seasons[year] = races }
        }
        if (seasons.isEmpty()) return emptyList()

        // First season (by year) that still has a weekend to come; else the latest one
        val now = System.currentTimeMillis()
        val years = seasons.keys.sorted()
        val pick = years.firstOrNull { y ->
            seasons[y]!!.any { WeekendTimeline.weekendEnd(it) > now }
        } ?: years.last()
        return seasons[pick]!!
    }

    /** Drop the cache and tell every screen (after a sync changed something) */
    fun invalidate() {
        synchronized(this) { cache = null; cacheDay = -1L }
        notifyChanged()
    }

    fun source(context: Context): Source =
        if (importedFile(context.applicationContext).exists()) Source.IMPORTED else Source.BUNDLED

    // ------------------------------------------------------------------
    // IMPORT
    // ------------------------------------------------------------------

    /**
     * Reads and validates a file picked by the user. Does NOT save anything,
     * so the caller can show a confirmation first. Call from a background thread.
     */
    @Throws(ScheduleFormatException::class)
    fun readFromUri(context: Context, uri: Uri): List<Race> {
        val bytes: ByteArray? = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8 * 1024)
                val out = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_IMPORT_BYTES) {
                        throw ScheduleFormatException("File is too large to be a race schedule.")
                    }
                }
                out.toByteArray()
            }
        } catch (e: ScheduleFormatException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not open $uri", e)
            null
        }

        if (bytes == null) throw ScheduleFormatException("Couldn't open that file.")

        return RaceJsonParser.parse(String(bytes, Charsets.UTF_8))
    }

    /** Saves already-validated races as the active schedule. Call from a background thread. */
    fun saveImported(context: Context, races: List<Race>) {
        val app = context.applicationContext
        val target = importedFile(app)
        target.parentFile?.mkdirs()

        val json = GsonBuilder().setPrettyPrinting().create()
            .toJson(mapOf("races" to races))

        // Write to a temp file first so a crash mid-write can't leave a broken schedule
        val tmp = File(target.parentFile, "$IMPORT_FILE.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IllegalStateException("Couldn't save the schedule.")
            }
        }

        synchronized(this) { cache = null; cacheDay = -1L }
        notifyChanged()
    }

    /** Drops the imported schedule and goes back to the one bundled with the app. */
    fun resetToBundled(context: Context) {
        val app = context.applicationContext
        importedFile(app).delete()
        synchronized(this) { cache = null; cacheDay = -1L }
        notifyChanged()
    }

    // ------------------------------------------------------------------
    // CHANGE LISTENERS (UI refresh)
    // ------------------------------------------------------------------

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        mainHandler.post { listeners.forEach { it() } }
    }

    // ------------------------------------------------------------------
    // INTERNAL
    // ------------------------------------------------------------------

    private fun importedFile(context: Context): File =
        File(File(context.filesDir, IMPORT_DIR), IMPORT_FILE)

    private fun loadImported(context: Context): List<Race>? {
        val file = importedFile(context)
        if (!file.exists()) return null
        return try {
            RaceJsonParser.parse(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Imported schedule is unreadable, falling back to bundled", e)
            null
        }
    }

    /** Every assets/motogp_<year>.json, keyed by year */
    private fun loadBundledSeasons(context: Context): Map<Int, List<Race>> {
        val names = try {
            context.assets.list("")?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val out = sortedMapOf<Int, List<Race>>()
        names.forEach { name ->
            val year = ASSET_PATTERN.matchEntire(name)?.groupValues?.get(1)?.toInt() ?: return@forEach
            try {
                val json = context.assets.open(name).bufferedReader().use { it.readText() }
                out[year] = RaceJsonParser.parse(json)
            } catch (e: Exception) {
                Log.e(TAG, "Bundled $name is unreadable", e)
            }
        }
        return out
    }
}
