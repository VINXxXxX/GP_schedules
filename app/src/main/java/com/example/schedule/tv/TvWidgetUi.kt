package com.example.schedule.tv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.example.schedule.R
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.season
import com.example.schedule.model.Race
import com.example.schedule.ui.Format
import com.example.schedule.tv.TvSupport.State
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Draws the TV widget (Pit Wall):
 *
 *   [R19] SEPANG   MALAYSIA · 30 OCT – 1 NOV · SEPANG INTERNATIONAL CIRCUIT     ☁ 31°
 *   ● FP1 LIVE · NEXT PR 12:30 PM · ENDS IN                                  00:25:12
 *   [FP1 FRI 8:15] [PR FRI 12:30] [FP2 SAT 7:40] … [GP SUN 12:30]
 *   NEXT RND · LUSAIL · 6 NOV                                                  19 / 22
 *
 * The timer is a Chronometer, so it ticks on the TV home screen without updates.
 * State changes (session start / end, midnight) come from SessionScheduler's exact alarm.
 */
object TvWidgetUi {

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SOFT = 0xFFE8E8EC.toInt()
    private const val MUTED = 0xFFC6C6CE.toInt()
    private const val FAINT = 0xFF8E8E98.toInt() // lighter than the app: sits on glass
    private const val DIM = 0xFF9C9CA6.toInt()
    private const val RED_TEXT = 0xFFFF6B60.toInt()
    private const val ON_RED_SOFT = 0xFFFFE1DE.toInt()

    /** tile, label, day, time */
    private val CELLS = arrayOf(
        intArrayOf(R.id.tv_cell_1, R.id.tv_cell_1_label, R.id.tv_cell_1_day, R.id.tv_cell_1_time),
        intArrayOf(R.id.tv_cell_2, R.id.tv_cell_2_label, R.id.tv_cell_2_day, R.id.tv_cell_2_time),
        intArrayOf(R.id.tv_cell_3, R.id.tv_cell_3_label, R.id.tv_cell_3_day, R.id.tv_cell_3_time),
        intArrayOf(R.id.tv_cell_4, R.id.tv_cell_4_label, R.id.tv_cell_4_day, R.id.tv_cell_4_time),
        intArrayOf(R.id.tv_cell_5, R.id.tv_cell_5_label, R.id.tv_cell_5_day, R.id.tv_cell_5_time),
        intArrayOf(R.id.tv_cell_6, R.id.tv_cell_6_label, R.id.tv_cell_6_day, R.id.tv_cell_6_time),
        intArrayOf(R.id.tv_cell_7, R.id.tv_cell_7_label, R.id.tv_cell_7_day, R.id.tv_cell_7_time),
        intArrayOf(R.id.tv_cell_8, R.id.tv_cell_8_label, R.id.tv_cell_8_day, R.id.tv_cell_8_time),
        intArrayOf(R.id.tv_cell_9, R.id.tv_cell_9_label, R.id.tv_cell_9_day, R.id.tv_cell_9_time),
        intArrayOf(R.id.tv_cell_10, R.id.tv_cell_10_label, R.id.tv_cell_10_day, R.id.tv_cell_10_time)
    )

    enum class Mode {
        /** One tile per session (wide widget) */
        TILES,
        /** One line per day (narrow widget, tiles would be unreadable) */
        AGENDA
    }

    /** Width (dp) the tiles need to stay readable: ~62 dp per tile + padding */
    fun tilesMinWidth(sessionCount: Int): Float =
        sessionCount.coerceIn(1, CELLS_MAX) * 62f + 30f

    private const val CELLS_MAX = 10

    fun build(
        context: Context,
        races: List<Race>,
        snap: WeekendSnapshot?,
        now: Long,
        mode: Mode = Mode.TILES
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tv)
        views.setOnClickPendingIntent(R.id.tv_root, openIntent(context))

        if (snap == null) {
            bindEmpty(views)
            return views
        }

        val race = snap.race

        // ---------- HEADER ----------
        views.setTextViewText(R.id.tv_round, TvSupport.roundText(race))
        views.setTextViewText(R.id.tv_title, Format.city(race))
        views.setTextViewText(
            R.id.tv_meta,
            listOf(
                Format.country(race),
                Format.weekendRange(race, snap.sessions),
                race.track.uppercase(Locale.ENGLISH)
            ).filter { it.isNotBlank() }.joinToString("  ·  ")
        )
        bindWeather(context, views, race, now)

        // ---------- STATUS ----------
        bindStatus(context, views, snap, now)

        // ---------- TILES / AGENDA ----------
        val tiles = mode == Mode.TILES
        val shown = snap.sessions.let { if (it.size <= CELLS.size) it else it.drop(it.size - CELLS.size) }
        if (tiles) {
            CELLS.forEachIndexed { i, ids ->
                val s = shown.getOrNull(i)
                if (s == null) {
                    views.setViewVisibility(ids[0], View.GONE)
                } else {
                    views.setViewVisibility(ids[0], View.VISIBLE)
                    bindCell(context, views, ids, s, TvSupport.state(s, snap, now), now)
                }
            }
        } else {
            views.setTextViewText(R.id.tv_agenda, agenda(context, snap, now))
        }
        views.setViewVisibility(R.id.tv_cells, if (tiles && shown.isNotEmpty()) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.tv_agenda, if (!tiles && shown.isNotEmpty()) View.VISIBLE else View.GONE)

        // ---------- FOOTER ----------
        val following = races.getOrNull(snap.position) // position is 1-based → the next index
        views.setTextViewText(
            R.id.tv_footer_left,
            when {
                snap.seasonOver -> "SEASON ${race.season()} · ${snap.totalRounds} ROUNDS"
                following != null -> "NEXT RND · ${Format.city(following)}" +
                    (firstDate(following)?.let { " · $it" } ?: "")
                else -> "FINAL ROUND"
            }
        )
        views.setTextViewText(R.id.tv_footer_right, "${snap.position} / ${snap.totalRounds}")

        return views
    }

    // ------------------------------------------------------------------

    private fun bindStatus(context: Context, views: RemoteViews, snap: WeekendSnapshot, now: Long) {
        val live = snap.live
        val next = snap.next
        val elapsedNow = SystemClock.elapsedRealtime()

        when {
            snap.seasonOver -> {
                status(views, "SEASON ${snap.race.season()} COMPLETE · IMPORT THE NEXT SEASON", MUTED)
                showText(views, "DONE")
            }

            live != null -> {
                val after = snap.sessions.firstOrNull { it.start >= live.end }
                status(
                    views,
                    "● ${TvSupport.label(live)} LIVE" +
                        (after?.let { " · NEXT ${TvSupport.label(it)} ${Format.clock(context, it.start)}" } ?: "") +
                        "  ·  ENDS IN",
                    RED_TEXT
                )
                showTimer(views, elapsedNow + (live.end - now))
            }

            next != null -> {
                val untilStart = next.start - now
                val head = "NEXT ${TvSupport.label(next)} · ${Format.dayShort(next.start)} ${Format.clock(context, next.start)}"
                if (untilStart <= TvSupport.DAY_MS) {
                    status(views, "$head  ·  STARTS IN", RED_TEXT)
                    showTimer(views, elapsedNow + untilStart)
                } else {
                    status(views, head, RED_TEXT)
                    val days = Format.calendarDays(now, next.start)
                    showText(
                        views,
                        when {
                            days <= 0 -> "TODAY"
                            days == 1 -> "TOMORROW"
                            else -> "$days DAYS"
                        }
                    )
                }
            }

            else -> {
                status(views, "WEEKEND COMPLETE", MUTED)
                showText(views, "DONE")
            }
        }
    }

    private fun bindCell(
        context: Context,
        views: RemoteViews,
        ids: IntArray,
        s: TimedSession,
        state: State,
        now: Long
    ) {
        val (tile, label, day, time) = ids.toList()

        val dayText = if (Format.calendarDays(now, s.start) == 0) "TODAY" else Format.dayShort(s.start)
        views.setTextViewText(label, if (state == State.LIVE) "● ${TvSupport.label(s)}" else TvSupport.label(s))
        views.setTextViewText(day, dayText)
        views.setTextViewText(time, Format.clock(context, s.start))

        when (state) {
            State.LIVE -> {
                views.setInt(tile, "setBackgroundResource", R.drawable.tv_cell_live)
                views.setTextColor(label, WHITE)
                views.setTextColor(day, ON_RED_SOFT)
                views.setTextColor(time, WHITE)
            }
            State.NEXT -> {
                views.setInt(tile, "setBackgroundResource", R.drawable.tv_cell_next)
                views.setTextColor(label, WHITE)
                views.setTextColor(day, MUTED)
                views.setTextColor(time, WHITE)
            }
            State.UPCOMING -> {
                views.setInt(tile, "setBackgroundResource", R.drawable.tv_cell_bg)
                views.setTextColor(label, if (TvSupport.isRace(s)) RED_TEXT else WHITE)
                views.setTextColor(day, MUTED)
                views.setTextColor(time, SOFT)
            }
            State.DONE -> {
                views.setInt(tile, "setBackgroundResource", R.drawable.tv_cell_done)
                views.setTextColor(label, FAINT)
                views.setTextColor(day, DIM)
                views.setTextColor(time, DIM)
            }
        }
    }

    /**
     * "FRI  FP1 8:15a · PR 12:30p" — one line per local day.
     * Done faded, live red with ●, next bold white, Sprint / GP red.
     */
    private fun agenda(context: Context, snap: WeekendSnapshot, now: Long): CharSequence {
        val sb = SpannableStringBuilder()
        snap.days.forEachIndexed { i, day ->
            if (i > 0) sb.append("\n")
            val dayDone = day.sessions.all { it.isDone(now) }
            sb.styled(
                day.label.substringBefore(" ") + "   ",
                when {
                    day.isToday -> RED_TEXT
                    dayDone -> FAINT
                    else -> MUTED
                },
                bold = true
            )
            day.sessions.forEachIndexed { j, s ->
                if (j > 0) sb.styled("  ·  ", if (dayDone) FAINT else DIM)
                val state = TvSupport.state(s, snap, now)
                val text = "${TvSupport.label(s)} ${shortClock(context, s.start)}"
                when (state) {
                    State.LIVE -> sb.styled("● $text", RED_TEXT, bold = true)
                    State.NEXT -> sb.styled(text, WHITE, bold = true)
                    State.DONE -> sb.styled(text, FAINT)
                    State.UPCOMING -> sb.styled(text, if (TvSupport.isRace(s)) RED_TEXT else SOFT)
                }
            }
        }
        return sb
    }

    private fun SpannableStringBuilder.styled(text: String, color: Int, bold: Boolean = false) {
        val start = length
        append(text)
        setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** "8:15a" / "12:30p" (or "08:15" in 24 h) — keeps a whole day on one line */
    private fun shortClock(context: Context, millis: Long): String =
        if (DateFormat.is24HourFormat(context)) {
            SimpleDateFormat("HH:mm", Locale.ENGLISH).format(millis)
        } else {
            SimpleDateFormat("h:mma", Locale.ENGLISH).format(millis)
                .lowercase(Locale.ENGLISH).removeSuffix("m")
        }

    private fun status(views: RemoteViews, text: String, color: Int) {
        views.setTextViewText(R.id.tv_status, text)
        views.setTextColor(R.id.tv_status, color)
    }

    private fun showTimer(views: RemoteViews, base: Long) {
        views.setChronometerCountDown(R.id.tv_timer, true)
        views.setChronometer(R.id.tv_timer, base, null, true)
        views.setViewVisibility(R.id.tv_timer, View.VISIBLE)
        views.setViewVisibility(R.id.tv_timer_text, View.GONE)
    }

    private fun showText(views: RemoteViews, text: String) {
        views.setChronometer(R.id.tv_timer, SystemClock.elapsedRealtime(), null, false)
        views.setViewVisibility(R.id.tv_timer, View.GONE)
        views.setTextViewText(R.id.tv_timer_text, text)
        views.setViewVisibility(R.id.tv_timer_text, View.VISIBLE)
    }

    /** Track weather, race week only (cached, works offline) */
    private fun bindWeather(context: Context, views: RemoteViews, race: Race, now: Long) {
        val wx = try {
            com.example.schedule.weather.Weather.headline(context, race, now)
        } catch (_: Exception) {
            null
        }
        if (wx == null) {
            views.setViewVisibility(R.id.tv_weather_icon, View.GONE)
            views.setViewVisibility(R.id.tv_weather_text, View.GONE)
            return
        }
        views.setImageViewResource(R.id.tv_weather_icon, wx.icon)
        views.setTextViewText(R.id.tv_weather_text, "${wx.tMax}°")
        views.setViewVisibility(R.id.tv_weather_icon, View.VISIBLE)
        views.setViewVisibility(R.id.tv_weather_text, View.VISIBLE)
    }

    /** "6 NOV" from the first timed session, null if none has a time yet */
    private fun firstDate(race: Race): String? =
        WeekendTimeline.build(race).firstOrNull()?.let { Format.dayMonth(it.start) }

    private fun bindEmpty(views: RemoteViews) {
        views.setTextViewText(R.id.tv_round, "--")
        views.setTextViewText(R.id.tv_title, "NO SCHEDULE")
        views.setTextViewText(R.id.tv_meta, "")
        status(views, "Open GP Schedule and import a season", MUTED)
        showText(views, "--")
        views.setViewVisibility(R.id.tv_cells, View.GONE)
        views.setViewVisibility(R.id.tv_agenda, View.GONE)
        views.setViewVisibility(R.id.tv_weather_icon, View.GONE)
        views.setViewVisibility(R.id.tv_weather_text, View.GONE)
        views.setTextViewText(R.id.tv_footer_left, "")
        views.setTextViewText(R.id.tv_footer_right, "")
    }

    /** Tap → redraw now (no app opens) */
    private fun openIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            7800,
            Intent(context, com.example.schedule.WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
