package com.example.schedule.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.example.schedule.R
import com.example.schedule.WidgetRefreshReceiver
import com.example.schedule.data.SessionCatalog
import com.example.schedule.data.SessionRoles
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.season
import com.example.schedule.data.shortLabel
import com.example.schedule.model.Race
import com.example.schedule.ui.Format
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Draws the 4×2 MotoGP widget (Pit Wall).
 *
 * Race week:  round · country · dates, alert setting, live/next status with a ticking
 *             countdown, every session by local day (live red ●, done faded), next round,
 *             season progress.
 * Off week:   next round, days to go, its key session times and the three rounds after it.
 */
object WidgetUi {

    // ---------- PALETTE ----------
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SOFT = 0xFFE8E8EC.toInt()
    private const val MUTED = 0xFFC6C6CE.toInt()
    private const val FAINT = 0xFFA4A4AD.toInt()
    private const val DIM = 0xFF85858F.toInt()
    const val RED = 0xFFE5261B.toInt()
    private const val RED_TEXT = 0xFFFF6B60.toInt()

    enum class Mode {
        /** Short height: hero + one-line agenda */
        SHORT,
        /** Normal 4×2: hero + day columns */
        MEDIUM,
        /** Tall: + track name */
        TALL
    }

    fun build(
        context: Context,
        layout: Int,
        races: List<Race>,
        snap: WeekendSnapshot?,
        now: Long,
        mode: Mode
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)

        if (snap == null) {
            bindEmpty(context, views)
            return views
        }

        val race = snap.race
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))

        val raceWeek = isRaceWeek(snap, now) || snap.seasonOver
        val range = Format.weekendRange(race, snap.sessions)
        val country = Format.country(race)

        // ---------- HEADER ----------
        val roundText = "R" + race.round.toString().padStart(2, '0')
        // Always on red; "NEXT R02" in an off week
        views.setTextViewText(R.id.round_chip, if (raceWeek) roundText else "NEXT $roundText")
        views.setTextViewText(R.id.header_meta, if (country.isNotEmpty()) "$country · $range" else range)
        views.setTextViewText(R.id.widget_title, Format.city(race))
        views.setTextViewText(R.id.widget_track, race.track.uppercase(Locale.ENGLISH))
        views.setTextViewText(R.id.watermark, race.round.toString().padStart(2, '0'))
        bindWeather(context, views, race, now)

        // ---------- HERO ----------
        if (raceWeek) bindRaceWeekHero(context, views, snap, now) else bindOffWeekHero(context, views, snap, now)

        // ---------- BODY ----------
        val showColumns = mode != Mode.SHORT
        views.setViewVisibility(R.id.widget_track, if (mode == Mode.TALL) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.divider, if (showColumns) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.days_row, if (showColumns) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.agenda, if (showColumns) View.GONE else View.VISIBLE)

        val columns: List<CharSequence> =
            if (raceWeek) dayColumns(context, snap, now) else nextRounds(races, snap)
        val dayIds = intArrayOf(R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4)
        dayIds.forEachIndexed { i, id ->
            val text = columns.getOrNull(i)
            if (showColumns && text != null) {
                views.setTextViewText(id, text)
                views.setViewVisibility(id, View.VISIBLE)
            } else {
                views.setViewVisibility(id, if (showColumns && i < 3) View.INVISIBLE else View.GONE)
            }
        }
        if (!showColumns) {
            views.setTextViewText(R.id.agenda, if (raceWeek) agendaText(context, snap, now) else keySessions(context, snap))
        }

        // ---------- FOOTER ----------
        val following = races.getOrNull(snap.position) // position is 1-based → next index
        views.setTextViewText(
            R.id.footer_left,
            when {
                snap.seasonOver -> "SEASON ${race.season()} COMPLETE"
                !raceWeek -> race.track.uppercase(Locale.ENGLISH)
                following != null -> "NEXT RND · ${Format.city(following)} ${firstDay(following)}"
                else -> "FINAL ROUND"
            }
        )
        views.setTextViewText(R.id.footer_right, "${snap.position} / ${snap.totalRounds}")
        // Race week + day columns: the sessions need the room more than the footer
        views.setViewVisibility(
            R.id.footer_row,
            if (raceWeek && showColumns && !snap.seasonOver) View.GONE else View.VISIBLE
        )
        views.setProgressBar(R.id.season_progress, snap.totalRounds, snap.completedRounds, false)

        return views
    }

    // ------------------------------------------------------------------
    // HERO
    // ------------------------------------------------------------------

    private fun bindRaceWeekHero(context: Context, views: RemoteViews, snap: WeekendSnapshot, now: Long) {
        val live = snap.live
        val next = snap.next
        val elapsedNow = SystemClock.elapsedRealtime()

        when {
            snap.seasonOver -> {
                views.setTextViewText(R.id.hero_label, "IMPORT NEXT SEASON IN SETTINGS")
                views.setTextColor(R.id.hero_label, MUTED)
                views.setTextViewText(R.id.hero_sub, "SEASON ${snap.race.season()}")
                showText(views, "DONE")
            }

            live != null -> {
                val after = snap.sessions.firstOrNull { it.start >= live.end }
                views.setTextViewText(
                    R.id.hero_label,
                    "● ${lbl(live)} LIVE" + (after?.let { " · NEXT ${lbl(it)} ${clock(context, it.start)}" } ?: "")
                )
                views.setTextColor(R.id.hero_label, RED_TEXT)
                views.setTextViewText(R.id.hero_sub, "${lbl(live)} ENDS IN")
                // Ticks on the home screen by itself; end time is the session's planned length
                showTimer(views, base = elapsedNow + (live.end - now), countDown = true)
            }

            next != null -> {
                views.setTextViewText(
                    R.id.hero_label,
                    "NEXT ${lbl(next)} · ${Format.dayShort(next.start)} ${clock(context, next.start)}"
                )
                views.setTextColor(R.id.hero_label, RED_TEXT)
                val untilStart = next.start - now
                if (untilStart <= DAY_MS) {
                    views.setTextViewText(R.id.hero_sub, "${lbl(next)} STARTS IN")
                    showTimer(views, base = elapsedNow + untilStart, countDown = true)
                } else {
                    views.setTextViewText(R.id.hero_sub, "${lbl(next)} STARTS")
                    showText(views, daysToGo(now, next.start))
                }
            }

            else -> {
                views.setTextViewText(R.id.hero_label, "WEEKEND COMPLETE")
                views.setTextColor(R.id.hero_label, MUTED)
                views.setTextViewText(R.id.hero_sub, "")
                showText(views, "DONE")
            }
        }
    }

    private fun bindOffWeekHero(context: Context, views: RemoteViews, snap: WeekendSnapshot, now: Long) {
        // Left: the two races. Right: first session + days to go.
        views.setTextViewText(R.id.hero_label, keySessions(context, snap))
        views.setTextColor(R.id.hero_label, SOFT)

        val first = snap.sessions.firstOrNull()
        val days = Format.calendarDays(now, first?.start ?: snap.weekendStart)
        views.setTextViewText(
            R.id.hero_sub,
            first?.let { "${lbl(it)} ${Format.dayShort(it.start)} ${clock(context, it.start)}" } ?: "FIRST SESSION"
        )
        showText(views, when {
            days <= 0 -> "TODAY"
            days == 1 -> "TOMORROW"
            else -> "$days DAYS"
        })
    }

    /** "SPR SAT 1:30p · GP SUN 1:30p" (falls back to the first session) */
    private fun keySessions(context: Context, snap: WeekendSnapshot): CharSequence {
        val picks = snap.sessions.filter { SessionRoles.isRace(it.session) }
            .ifEmpty { listOfNotNull(snap.sessions.firstOrNull()) }
        return picks.joinToString("  ·  ") { "${lbl(it)} ${Format.dayShort(it.start)} ${clock(context, it.start)}" }
    }

    /** Widget label: "GP" reads better than "RACE" in tight columns */
    private fun lbl(s: TimedSession): String =
        if (SessionCatalog.canonical(s.session.sessionName) == "race") "GP" else s.shortLabel

    private fun showTimer(views: RemoteViews, base: Long, countDown: Boolean) {
        views.setChronometerCountDown(R.id.hero_timer, countDown)
        views.setChronometer(R.id.hero_timer, base, null, true)
        views.setViewVisibility(R.id.hero_timer, View.VISIBLE)
        views.setViewVisibility(R.id.hero_text, View.GONE)
    }

    private fun showText(views: RemoteViews, text: String) {
        views.setChronometer(R.id.hero_timer, SystemClock.elapsedRealtime(), null, false)
        views.setViewVisibility(R.id.hero_timer, View.GONE)
        views.setTextViewText(R.id.hero_text, text)
        views.setViewVisibility(R.id.hero_text, View.VISIBLE)
    }

    // ------------------------------------------------------------------
    // HEADER BITS
    // ------------------------------------------------------------------

    /** Track weather icon + high temp, race week only (cached daily, works offline) */
    private fun bindWeather(context: Context, views: RemoteViews, race: Race, now: Long) {
        val wx = com.example.schedule.weather.Weather.headline(context, race, now)
        if (wx == null) {
            views.setViewVisibility(R.id.weather_icon, View.GONE)
            views.setViewVisibility(R.id.weather_text, View.GONE)
            return
        }
        views.setImageViewResource(R.id.weather_icon, wx.icon)
        views.setTextViewText(R.id.weather_text, "${wx.tMax}°")
        views.setViewVisibility(R.id.weather_icon, View.VISIBLE)
        views.setViewVisibility(R.id.weather_text, View.VISIBLE)
    }

    /** From Monday 00:00 of the week the weekend starts in. */
    private fun isRaceWeek(snap: WeekendSnapshot, now: Long): Boolean {
        val monday = Calendar.getInstance().apply {
            timeInMillis = snap.weekendStart
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
        }
        return now >= monday.timeInMillis && now < snap.weekendEnd
    }

    // ------------------------------------------------------------------
    // COLUMNS
    // ------------------------------------------------------------------

    /** One column per local day: "FRI 05" then each session styled by state; TBA sessions at the end. */
    private fun dayColumns(context: Context, snap: WeekendSnapshot, now: Long): List<CharSequence> {
        val untimed = snap.race.sessions.filter { s -> snap.sessions.none { it.session == s } }

        return snap.days.map { day ->
            val sb = SpannableStringBuilder()
            sb.appendStyled(day.label, color = if (day.isToday) RED_TEXT else MUTED, bold = true, scale = 0.9f)
            // Q1 and Q2 run back to back: "Q1·Q2 9:20 AM" keeps Saturday on fewer lines
            var i = 0
            while (i < day.sessions.size) {
                val s = day.sessions[i]
                val n = day.sessions.getOrNull(i + 1)
                sb.append("\n")
                if (n != null && SessionCatalog.canonical(s.session.sessionName) == "q1" &&
                    SessionCatalog.canonical(n.session.sessionName) == "q2" &&
                    !s.isLive(now) && !n.isLive(now) && s.isDone(now) == n.isDone(now) &&
                    snap.next != n
                ) {
                    appendSession(context, sb, s, snap, now, labelOverride = "Q1·Q2")
                    i += 2
                } else {
                    appendSession(context, sb, s, snap, now)
                    i++
                }
            }
            // Sessions with no published time go on the day their dayOffset belongs to
            untimed.filter { u ->
                day.sessions.any { it.session.dayOffset == u.dayOffset }
            }.forEach { u ->
                sb.append("\n")
                sb.appendStyled("${u.shortLabel(snap.race)} TBA", color = FAINT)
            }
            sb as CharSequence
        }
    }

    /** Off week: the three rounds after the featured one — "R02 · 12 MAR", city, country */
    private fun nextRounds(races: List<Race>, snap: WeekendSnapshot): List<CharSequence> =
        races.drop(snap.position).take(3).map { r ->
            val sb = SpannableStringBuilder()
            val start = WeekendTimeline.build(r).firstOrNull()?.start
            sb.appendStyled(
                "R${r.round.toString().padStart(2, '0')} · ${start?.let { Format.dayMonth(it) } ?: r.race}",
                color = MUTED, bold = true, scale = 0.9f
            )
            sb.append("\n")
            sb.appendStyled(Format.city(r).titled(), color = WHITE, bold = true)
            Format.country(r).takeIf { it.isNotEmpty() }?.let {
                sb.append("\n")
                sb.appendStyled(it.titled(), color = MUTED)
            }
            sb
        }

    private fun String.titled() = lowercase(Locale.ENGLISH).split(" ")
        .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }

    /** Short size: the active day's sessions on one line */
    private fun agendaText(context: Context, snap: WeekendSnapshot, now: Long): CharSequence {
        val day = snap.activeDay ?: return ""
        val sb = SpannableStringBuilder()
        sb.appendStyled(day.label, color = if (day.isToday) RED_TEXT else MUTED, bold = true, scale = 0.9f)
        day.sessions.forEach { s ->
            sb.append("   ")
            appendSession(context, sb, s, snap, now)
        }
        return sb
    }

    private fun appendSession(
        context: Context,
        sb: SpannableStringBuilder,
        s: TimedSession,
        snap: WeekendSnapshot,
        now: Long,
        labelOverride: String? = null
    ) {
        val name = labelOverride ?: lbl(s)
        val isNext = snap.live == null && snap.next == s
        val race = SessionRoles.isRace(s.session)
        when {
            s.isLive(now) -> {
                sb.appendStyled("● $name ", color = RED_TEXT, bold = true)
                sb.appendStyled(clock(context, s.start), color = RED_TEXT, bold = true)
            }
            s.isDone(now) -> {
                sb.appendStyled("$name ", color = DIM)
                sb.appendStyled(clock(context, s.start), color = DIM)
            }
            isNext -> {
                sb.appendStyled("$name ", color = WHITE, bold = true)
                sb.appendStyled(clock(context, s.start), color = WHITE, bold = true)
            }
            else -> {
                sb.appendStyled("$name ", color = if (race) WHITE else SOFT, bold = true)
                sb.appendStyled(clock(context, s.start), color = if (race) WHITE else SOFT, bold = race)
            }
        }
    }

    private fun SpannableStringBuilder.appendStyled(
        text: CharSequence,
        color: Int,
        bold: Boolean = false,
        scale: Float = 1f
    ) {
        val start = length
        append(text)
        val end = length
        setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (scale != 1f) setSpan(RelativeSizeSpan(scale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // ------------------------------------------------------------------
    // FORMATTING
    // ------------------------------------------------------------------

    /** Phone's 12/24 h setting: "9:15 AM" or "21:15" */
    private fun clock(context: Context, millis: Long): CharSequence = Format.clock(context, millis)

    /** "12 MAR": local date of a round's first session */
    private fun firstDay(race: Race): String {
        val s = WeekendTimeline.build(race).firstOrNull()?.start ?: return race.race
        return Format.dayMonth(s)
    }

    /** Calendar days, not 24 h blocks: "TOMORROW", "3 DAYS". */
    private fun daysToGo(now: Long, target: Long): String {
        val days = Format.calendarDays(now, target)
        return when {
            days <= 0 -> "TODAY"
            days == 1 -> "TOMORROW"
            else -> "$days DAYS"
        }
    }

    // ------------------------------------------------------------------

    private fun bindEmpty(context: Context, views: RemoteViews) {
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))
        views.setTextViewText(R.id.round_chip, "--")
        views.setTextViewText(R.id.header_meta, "")
        views.setTextViewText(R.id.widget_title, "NO SCHEDULE")
        views.setTextViewText(R.id.watermark, "")
        views.setTextViewText(R.id.hero_label, "Import a schedule in Settings")
        views.setTextColor(R.id.hero_label, MUTED)
        views.setTextViewText(R.id.hero_sub, "")
        showText(views, "--")
        views.setViewVisibility(R.id.widget_track, View.GONE)
        views.setViewVisibility(R.id.divider, View.GONE)
        views.setViewVisibility(R.id.days_row, View.GONE)
        views.setViewVisibility(R.id.agenda, View.GONE)
        views.setTextViewText(R.id.footer_left, "")
        views.setTextViewText(R.id.footer_right, "")
        views.setProgressBar(R.id.season_progress, 1, 0, false)
        views.setViewVisibility(R.id.weather_icon, View.GONE)
        views.setViewVisibility(R.id.weather_text, View.GONE)
    }

    /** Tap → redraw now (with a short vibration) */
    private fun refreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7700,
            Intent(context, WidgetRefreshReceiver::class.java).putExtra("haptic", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
