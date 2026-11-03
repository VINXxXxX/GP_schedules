package com.example.schedule.tv

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.schedule.MainActivity
import com.example.schedule.SchedulerCoordinator
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.SessionRoles
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.season
import com.example.schedule.model.Race
import com.example.schedule.tv.TvSupport.State
import com.example.schedule.ui.Format
import com.example.schedule.ui.Pit
import com.example.schedule.ui.Pit.fit
import com.example.schedule.ui.Pit.styled
import com.example.schedule.ui.PitPalette
import java.util.Locale

/**
 * Full-screen weekend for the TV. Read-only screen + one row of big buttons, so the
 * remote only ever moves left / right along the bottom bar.
 *
 *   ROUND 19 · 30 OCT – 1 NOV      FRI 30  TODAY     SAT 31          SUN 01
 *   SEPANG                         ┌FP1   8:15 AM┐   ┌FP2  7:40 AM┐  ┌WUP  8:10 AM┐
 *   MALAYSIA · SEPANG INTL         │DONE · Practice│  │NEXT · in 10h│  │Warm up     │
 *   ┌ NEXT · FREE PRACTICE 2 ┐     └──────────────┘   …               …
 *   │ 10:36:09               │
 *   └ SAT 31 · 7:40 AM       ┘
 *   [◀ PREVIOUS ROUND] [THIS WEEKEND] [NEXT ROUND ▶]        SEASON 2026 · 19/22   [SETTINGS]
 *
 * CH+/CH− and media skip keys also change round.
 */
class TvScheduleActivity : AppCompatActivity() {

    private val p: PitPalette = Pit.DARK
    private val condensedBold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)

    private var races: List<Race> = emptyList()
    private var index = 0
    private var currentIndex = 0
    private var snap: WeekendSnapshot? = null
    private var highlightStart: Long = -1L

    private lateinit var meta: TextView
    private lateinit var city: TextView
    private lateinit var place: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusLabel: TextView
    private lateinit var timer: TextView
    private lateinit var statusSub: TextView
    private lateinit var seasonLine: TextView
    private lateinit var days: LinearLayout
    private lateinit var prevButton: TextView
    private lateinit var thisButton: TextView
    private lateinit var nextButton: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val s = snap
            val boundary = s?.live?.end ?: s?.next?.start
            if (boundary != null && now >= boundary) render() else updateTimer(now)
            handler.postDelayed(this, 1_000L - now % 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.applyWindowColors(this, p)
        setContentView(buildLayout())
        // Also redraws the TV widget and the channel row
        SchedulerCoordinator.forceRefresh(this)
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { step(+1); true }
        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { step(-1); true }
        else -> super.onKeyDown(keyCode, event)
    }

    // ------------------------------------------------------------------

    private fun load(intent: Intent?) {
        races = RaceRepository.getRaces(this)
        highlightStart = intent?.getLongExtra(TvChannel.EXTRA_FOCUS_START, -1L) ?: -1L
        val current = WeekendTimeline.snapshot(races, System.currentTimeMillis())
        currentIndex = ((current?.position ?: 1) - 1).coerceIn(0, (races.size - 1).coerceAtLeast(0))
        index = currentIndex
        render()
        listOf(nextButton, prevButton, thisButton).firstOrNull { it.isEnabled }?.requestFocus()
    }

    private fun step(delta: Int) {
        if (races.isEmpty()) return
        val target = (index + delta).coerceIn(0, races.lastIndex)
        if (target == index) return
        index = target
        highlightStart = -1L
        render()
    }

    private fun jumpToCurrent() {
        if (index == currentIndex) return
        index = currentIndex
        highlightStart = -1L
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val race = races.getOrNull(index)
        if (race == null) {
            snap = null
            meta.text = ""
            city.text = "NO SCHEDULE"
            place.text = "Open SETTINGS to import a season"
            statusCard.visibility = View.GONE
            seasonLine.text = ""
            days.removeAllViews()
            return
        }

        val s = WeekendTimeline.snapshot(listOf(race), now)
        snap = s
        val sessions = s?.sessions.orEmpty()

        meta.text = "ROUND ${race.round.toString().padStart(2, '0')}  ·  ${Format.weekendRange(race, sessions)}"
        city.text = Format.city(race)
        place.text = listOf(Format.country(race), race.track.uppercase(Locale.ENGLISH))
            .filter { it.isNotEmpty() }.joinToString("  ·  ")
        seasonLine.text = "SEASON ${race.season()}  ·  ROUND ${index + 1} / ${races.size}"
        statusCard.visibility = View.VISIBLE

        setEnabled(prevButton, index > 0)
        setEnabled(nextButton, index < races.lastIndex)
        setEnabled(thisButton, index != currentIndex)

        // ---------- DAY COLUMNS ----------
        days.removeAllViews()
        s?.days?.forEachIndexed { i, day ->
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

            // "FRI 30" + TODAY pill on one line
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Pit.dp(context, 2f), 0, 0, Pit.dp(context, 4f))
            }
            head.addView(TextView(this).apply {
                styled(condensedBold, 22f, if (day.isToday) p.text else p.muted, 0.06f)
                text = day.label
            })
            if (day.isToday) head.addView(TextView(this).apply {
                styled(condensedBold, 12f, p.onAccent, 0.1f)
                text = "TODAY"
                background = Pit.rounded(context, p.accent, 5f)
                setPadding(Pit.dp(context, 7f), Pit.dp(context, 2f), Pit.dp(context, 7f), Pit.dp(context, 2f))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Pit.dp(this@TvScheduleActivity, 10f)
            })
            column.addView(head)

            day.sessions.forEach { ts ->
                column.addView(sessionTile(
                    label = TvSupport.label(ts),
                    time = Format.clock(this, ts.start),
                    note = SessionRoles.note(race, ts.session),
                    state = TvSupport.state(ts, s, now),
                    start = ts.start, end = ts.end, now = now,
                    isRace = TvSupport.isRace(ts),
                    highlighted = ts.start == highlightStart
                ))
            }
            days.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = Pit.dp(this@TvScheduleActivity, 16f)
            })
        }

        val tba = race.sessions.size - sessions.size
        if (sessions.isEmpty() || tba > 0) {
            days.addView(TextView(this).apply {
                styled(condensed, 16f, p.muted)
                text = if (sessions.isEmpty()) "Session times are still TBA" else "+ $tba session${if (tba == 1) "" else "s"} TBA"
            })
        }

        updateTimer(now)
    }

    private fun updateTimer(now: Long) {
        val s = snap ?: return
        val live = s.live
        val next = s.next
        when {
            live != null -> {
                statusLabel.text = "●  LIVE  ·  ${live.longLabel}"
                statusLabel.setTextColor(p.accentText)
                timer.text = hms(live.end - now)
                statusSub.text = "ENDS ${Format.clock(this, live.end)}"
                statusCard.background = Pit.rounded(this, p.liveTint, 18f, p.accent)
            }
            next != null -> {
                statusLabel.text = "NEXT  ·  ${next.longLabel}"
                statusLabel.setTextColor(p.accentText)
                val until = next.start - now
                timer.text = if (until <= TvSupport.DAY_MS) hms(until) else {
                    val d = Format.calendarDays(now, next.start)
                    if (d == 1) "TOMORROW" else "$d DAYS"
                }
                statusSub.text = "${Format.dayLabel(next.start)}  ·  ${Format.clock(this, next.start)}"
                statusCard.background = Pit.rounded(this, p.surface, 18f)
            }
            else -> {
                statusLabel.text = "WEEKEND COMPLETE"
                statusLabel.setTextColor(p.muted)
                timer.text = "DONE"
                statusSub.text = ""
                statusCard.background = Pit.rounded(this, p.surface, 18f)
            }
        }
    }

    private fun hms(ms: Long): String {
        val t = (ms / 1000L).coerceAtLeast(0)
        return String.format(Locale.ENGLISH, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }

    // ------------------------------------------------------------------
    // VIEWS
    // ------------------------------------------------------------------

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(p.bg) }

        // TV overscan-safe margins (~5 %)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Pit.dp(context, 56f), Pit.dp(context, 36f), Pit.dp(context, 56f), Pit.dp(context, 30f))
        }
        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        page.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---------- LEFT: where / when / next ----------
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, Pit.dp(context, 36f), 0)
        }
        top.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.34f))

        meta = TextView(this).apply { styled(condensedBold, 18f, p.accentText, 0.1f); maxLines = 1 }
        city = TextView(this).apply {
            styled(Pit.display(context), 54f, p.text)
            fit(28f, 54f)
            setPadding(0, Pit.dp(context, 6f), 0, 0)
        }
        place = TextView(this).apply {
            styled(condensed, 18f, p.soft, 0.06f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, Pit.dp(context, 6f), 0, 0)
        }
        left.addView(meta); left.addView(city); left.addView(place)

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Pit.dp(context, 20f), Pit.dp(context, 16f), Pit.dp(context, 20f), Pit.dp(context, 16f))
        }
        statusLabel = TextView(this).apply { styled(condensedBold, 18f, p.accentText, 0.06f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        timer = TextView(this).apply {
            styled(condensedBold, 60f, p.text)
            fit(30f, 60f)
            fontFeatureSettings = "tnum"
        }
        statusSub = TextView(this).apply { styled(condensed, 18f, p.soft, 0.04f) }
        statusCard.addView(statusLabel); statusCard.addView(timer); statusCard.addView(statusSub)
        left.addView(statusCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Pit.dp(this@TvScheduleActivity, 26f)
        })

        // ---------- RIGHT: the weekend ----------
        days = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(days, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.66f))

        // ---------- BOTTOM BAR: the only things the remote moves between ----------
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prevButton = button("◀   PREVIOUS ROUND") { step(-1) }
        thisButton = button("THIS WEEKEND") { jumpToCurrent() }
        nextButton = button("NEXT ROUND   ▶") { step(+1) }
        val settings = button("SETTINGS") { startActivity(Intent(this, MainActivity::class.java)) }
        seasonLine = TextView(this).apply { styled(condensed, 16f, p.muted, 0.08f); gravity = Gravity.END }

        listOf(prevButton, thisButton, nextButton).forEach {
            bar.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Pit.dp(this, 52f)).apply {
                marginEnd = Pit.dp(this@TvScheduleActivity, 12f)
            })
        }
        bar.addView(seasonLine, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = Pit.dp(this@TvScheduleActivity, 20f)
        })
        bar.addView(settings, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Pit.dp(this, 52f)))
        page.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Pit.dp(this@TvScheduleActivity, 18f)
        })

        return root
    }

    /** One session: label + time on top, state / what it decides below. Display only. */
    private fun sessionTile(
        label: String, time: String, note: String, state: State,
        start: Long, end: Long, now: Long, isRace: Boolean, highlighted: Boolean
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Pit.dp(context, 14f), Pit.dp(context, 10f), Pit.dp(context, 14f), Pit.dp(context, 10f))
            background = Pit.rounded(
                context,
                when (state) { State.LIVE -> p.liveTint; State.NEXT -> p.surface2; else -> p.surface },
                12f,
                when {
                    highlighted -> p.text
                    state == State.LIVE || state == State.NEXT -> p.accent
                    else -> null
                }
            )
            alpha = if (state == State.DONE) 0.5f else 1f
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            styled(condensedBold, 24f, if (isRace && state != State.LIVE) p.accentText else p.text, 0.02f)
            text = if (state == State.LIVE) "● $label" else label
            maxLines = 1
        })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(TextView(this).apply {
            styled(condensedBold, 20f, if (state == State.DONE) p.muted else p.soft)
            text = time
            maxLines = 1
        })
        tile.addView(top)

        val tag = when (state) {
            State.LIVE -> "LIVE · ${Format.left(now, end).uppercase(Locale.ENGLISH)}"
            State.NEXT -> "NEXT · ${Format.relative(now, start).uppercase(Locale.ENGLISH)}"
            State.DONE -> "DONE"
            State.UPCOMING -> null
        }
        val line = tag ?: note
        if (line.isNotBlank()) {
            tile.addView(TextView(this).apply {
                styled(condensed, 15f, if (state == State.LIVE || state == State.NEXT) p.accentText else p.muted)
                text = line
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, Pit.dp(context, 3f), 0, 0)
            })
        }
        tile.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Pit.dp(this@TvScheduleActivity, 8f)
        }
        return tile
    }

    private fun button(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        typeface = condensedBold
        textSize = 18f
        letterSpacing = 0.08f
        includeFontPadding = false
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        setPadding(Pit.dp(context, 24f), 0, Pit.dp(context, 24f), 0)
        background = buttonBg()
        setTextColor(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(p.bg, p.faint, p.text)
        ))
        setOnClickListener { if (isEnabled) onClick() }
        setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start()
        }
    }

    /** Disabled buttons stay in place (no layout jump) but can't take focus */
    private fun setEnabled(b: TextView, enabled: Boolean) {
        val hadFocus = b.isFocused
        b.isEnabled = enabled
        b.isFocusable = enabled
        if (hadFocus && !enabled) (if (nextButton.isEnabled) nextButton else prevButton).requestFocus()
    }

    /** Focused = solid white (obvious from the sofa); otherwise a dark pill */
    private fun buttonBg(): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), Pit.rounded(this@TvScheduleActivity, p.text, 26f))
        addState(intArrayOf(), Pit.rounded(this@TvScheduleActivity, p.surface2, 26f))
    }
}
