package com.example.schedule.data

import com.example.schedule.model.Race
import com.example.schedule.model.Session
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** A session with real start/end instants (epoch millis). */
data class TimedSession(
    val session: Session,
    val shortLabel: String,
    val longLabel: String,
    val start: Long,
    val end: Long
) {
    fun isLive(now: Long) = now in start until end
    fun isDone(now: Long) = now >= end
}

/** Sessions that fall on the same local calendar day, e.g. "FRI 05". */
data class DayGroup(
    val label: String,
    val isToday: Boolean,
    val sessions: List<TimedSession>
)

/** Everything a widget needs to draw itself at a given moment. */
data class WeekendSnapshot(
    val race: Race,
    /** 1-based position of this race in the loaded season */
    val position: Int,
    val totalRounds: Int,
    /** Rounds whose last session has finished */
    val completedRounds: Int,
    val sessions: List<TimedSession>,
    val live: TimedSession?,
    val next: TimedSession?,
    val days: List<DayGroup>,
    /** Day holding the live / next session (or the last day) */
    val activeDay: DayGroup?,
    val seasonOver: Boolean,
    val weekendStart: Long,
    val weekendEnd: Long
) {
    /** Between the first session's start and the last session's end */
    fun isRaceWeekend(now: Long) = now in weekendStart until weekendEnd
}

/**
 * Turns the JSON (IST times + dayOffset) into real instants and works out
 * what's live, what's next and what's done. Pure Kotlin so it can be unit tested.
 */
object WeekendTimeline {

    private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun build(race: Race): List<TimedSession> =
        race.sessions.mapNotNull { s ->
            val start = startMillis(race, s) ?: return@mapNotNull null
            TimedSession(
                session = s,
                shortLabel = s.shortLabel(race),
                longLabel = s.longLabel(race),
                start = start,
                end = start + s.durationMinutes(race) * 60_000L
            )
        }.sortedBy { it.start }

    fun startMillis(race: Race, s: Session): Long? = try {
        val p = race.race.split("-")
        val day = Calendar.getInstance(IST).apply {
            clear()
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            add(Calendar.DAY_OF_MONTH, s.dayOffset)
        }
        val date = String.format(
            Locale.ENGLISH, "%04d-%02d-%02d",
            day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)
        )
        SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH)
            .apply { timeZone = IST; isLenient = false }
            .parse("$date ${s.sessionTime.trim()}")
            ?.time
    } catch (_: Exception) {
        null
    }

    /** Last moment of a weekend (end of its last session). Falls back to Friday + 3 days. */
    fun weekendEnd(race: Race, sessions: List<TimedSession> = build(race)): Long =
        sessions.maxOfOrNull { it.end } ?: fallbackEnd(race)

    private fun fallbackEnd(race: Race): Long {
        val p = race.race.split("-")
        return Calendar.getInstance(IST).apply {
            clear()
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
            add(Calendar.DAY_OF_MONTH, 3)
        }.timeInMillis
    }

    /**
     * The race to show: the first one that hasn't finished yet.
     * Once the whole season is over, the final round with seasonOver = true.
     */
    fun snapshot(
        races: List<Race>,
        now: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): WeekendSnapshot? {
        if (races.isEmpty()) return null

        val built = races.map { it to build(it) }
        val ends = built.map { (race, sessions) -> weekendEnd(race, sessions) }

        var index = ends.indexOfFirst { now < it }
        val seasonOver = index < 0
        if (seasonOver) index = races.lastIndex

        val (race, sessions) = built[index]

        val live = sessions.firstOrNull { it.isLive(now) }
        val next = sessions.firstOrNull { it.start > now }
        val days = groupByDay(sessions, now, zone)

        val focus = live ?: next
        val activeDay = days.firstOrNull { d -> focus != null && d.sessions.contains(focus) }
            ?: days.lastOrNull()

        return WeekendSnapshot(
            race = race,
            position = index + 1,
            totalRounds = races.size,
            completedRounds = ends.count { it <= now },
            sessions = sessions,
            live = live,
            next = next,
            days = days,
            activeDay = activeDay,
            seasonOver = seasonOver,
            weekendStart = sessions.minOfOrNull { it.start } ?: ends[index],
            weekendEnd = ends[index]
        )
    }

    /**
     * Next moment the widget should redraw on its own: 24 h before a session
     * (countdown switches to a live timer), a session start, a session end.
     * Looks at the shown race and the one after it.
     */
    fun nextBoundary(races: List<Race>, now: Long): Long? {
        if (races.isEmpty()) return null
        val ends = races.map { weekendEnd(it) }
        val first = ends.indexOfFirst { now < it }.takeIf { it >= 0 } ?: return null

        return races.drop(first).take(2)
            .flatMap { build(it) }
            .flatMap { listOf(it.start - DAY_MS, it.start, it.end) }
            .filter { it > now + 1_000 }
            .minOrNull()
    }

    private fun groupByDay(
        sessions: List<TimedSession>,
        now: Long,
        zone: TimeZone
    ): List<DayGroup> {
        val keyFormat = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).apply { timeZone = zone }
        val labelFormat = SimpleDateFormat("EEE dd", Locale.ENGLISH).apply { timeZone = zone }
        val today = keyFormat.format(now)

        return sessions
            .groupBy { keyFormat.format(it.start) }
            .toSortedMap()
            .map { (key, list) ->
                DayGroup(
                    label = labelFormat.format(list.first().start).uppercase(Locale.ENGLISH),
                    isToday = key == today,
                    sessions = list
                )
            }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
