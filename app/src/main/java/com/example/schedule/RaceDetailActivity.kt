package com.example.schedule

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.example.schedule.data.Circuit
import com.example.schedule.data.Circuits
import com.example.schedule.data.RaceJsonParser
import com.example.schedule.weather.Weather
import com.example.schedule.weather.WeatherWorker
import java.util.Calendar
import java.util.TimeZone
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.SessionRoles
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.durationMinutes
import com.example.schedule.data.longLabel
import com.example.schedule.model.Race
import com.example.schedule.model.Session
import com.example.schedule.ui.Format
import com.example.schedule.ui.Pit
import com.example.schedule.ui.Pit.fit
import com.example.schedule.ui.Pit.styled
import com.example.schedule.ui.PitPalette
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Session page for one round (2027 format aware).
 * Day tabs in local days; each session shows its state, length, what it decides,
 * and a bell to switch its alert on or off.
 */
class RaceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RACE = "race_key"
    }

    /** One row: a timed session, or a session whose time is still TBA */
    private data class Entry(val session: Session, val timed: TimedSession?)

    private data class Day(val key: String, val label: String, val entries: List<Entry>)

    private lateinit var p: PitPalette
    private lateinit var race: Race
    private var sessions: List<TimedSession> = emptyList()
    private var days: List<Day> = emptyList()
    private var selected = 0

    private lateinit var tabs: LinearLayout
    private lateinit var timeline: LinearLayout
    private lateinit var alertsChip: TextView
    private lateinit var extras: LinearLayout

    private val weatherListener: () -> Unit = { if (::extras.isInitialized) renderExtras() }

    private val handler = Handler(Looper.getMainLooper())
    private val minuteTick = object : Runnable {
        override fun run() {
            renderTimeline()
            renderExtras()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_race_detail)
        p = Pit.palette(this)
        MainActivity.applyWindowColors(this, p)

        val key = intent.getStringExtra(EXTRA_RACE)
        val races = RaceRepository.getRaces(this)
        race = races.firstOrNull { NotificationScheduler.raceKey(it) == key }
            ?: WeekendTimeline.snapshot(races, System.currentTimeMillis())?.race
            ?: run { finish(); return }

        sessions = WeekendTimeline.build(race)
        days = buildDays()
        selected = defaultDay()

        findViewById<View>(R.id.detailRoot).setBackgroundColor(p.bg)

        findViewById<ImageButton>(R.id.backButton).apply {
            background = Pit.pressable(context, p.surface, 22f, Pit.ripple(p))
            imageTintList = ColorStateList.valueOf(p.text)
            setOnClickListener { goBack() }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        findViewById<TextView>(R.id.detailMeta).apply {
            text = "ROUND ${race.round.toString().padStart(2, '0')} · ${Format.weekendRange(race, sessions)}"
            styled(Pit.bodyMedium, 12f, p.muted, 0.12f)
        }

        alertsChip = findViewById(R.id.detailAlerts)
        alertsChip.background = Pit.rounded(this, p.surface, 18f)

        findViewById<TextView>(R.id.detailCity).apply {
            text = Format.city(race)
            styled(Pit.display(context), 40f, p.text)
            fit(22f, 40f)
        }
        findViewById<TextView>(R.id.detailSub).apply {
            text = listOf(Format.country(race), race.track.uppercase(Locale.ENGLISH))
                .filter { it.isNotEmpty() }.joinToString(" · ")
            styled(Pit.bodyMedium, 12f, p.muted, 0.1f)
        }
        findViewById<TextView>(R.id.detailFooter).styled(Pit.body, 11f, p.faint)

        tabs = findViewById(R.id.dayTabs)
        tabs.background = Pit.rounded(this, p.surface, 16f)
        timeline = findViewById(R.id.timeline)
        extras = findViewById(R.id.extras)

        renderTabs()
        renderTimeline()
        renderAlertCount()
        renderExtras()
        Weather.addListener(weatherListener)
    }

    /** Opened from a notification or widget: go to the main screen instead of leaving the app */
    private fun goBack() {
        if (isTaskRoot) {
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (::timeline.isInitialized) handler.post(minuteTick)
        WeatherWorker.enqueue(this)
    }

    override fun onPause() {
        handler.removeCallbacks(minuteTick)
        super.onPause()
    }

    override fun onDestroy() {
        Weather.removeListener(weatherListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // DAYS
    // ------------------------------------------------------------------

    private fun buildDays(): List<Day> {
        val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH)
        val timedByDay = sessions.groupBy { keyFmt.format(it.start) }.toSortedMap()

        val dayList = timedByDay.map { (k, list) ->
            k to list.map { Entry(it.session, it) }.toMutableList()
        }.toMutableList()

        // Sessions without a time (TBA): put them on the day their dayOffset points to
        race.sessions.filter { s -> sessions.none { it.session == s } }.forEach { s ->
            val sameOffset = sessions.firstOrNull { it.session.dayOffset == s.dayOffset }
            val target = sameOffset?.let { keyFmt.format(it.start) }
            val idx = dayList.indexOfFirst { it.first == target }
                .takeIf { it >= 0 } ?: s.dayOffset.coerceIn(0, (dayList.size - 1).coerceAtLeast(0))
            if (dayList.isEmpty()) dayList += Pair("tba", mutableListOf<Entry>())
            dayList[idx.coerceAtMost(dayList.lastIndex)].second += Entry(s, null)
        }

        return dayList.map { (k, entries) ->
            val first = entries.firstOrNull { it.timed != null }?.timed?.start
            Day(k, first?.let { Format.dayLabel(it) } ?: "TBA", entries)
        }
    }

    private fun defaultDay(): Int {
        val now = System.currentTimeMillis()
        val focus = sessions.firstOrNull { it.isLive(now) } ?: sessions.firstOrNull { it.start > now }
        val i = days.indexOfFirst { d -> d.entries.any { it.timed != null && it.timed == focus } }
        return if (i >= 0) i else 0
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        days.forEachIndexed { i, day ->
            val on = i == selected
            val tab = TextView(this).apply {
                text = day.label
                gravity = Gravity.CENTER
                styled(Pit.display(context), 15f, if (on) p.onAccent else p.muted, 0.06f)
                fit(10f, 15f)
                background = if (on) Pit.rounded(context, p.accent, 12f) else null
                isClickable = true
                isFocusable = true
                contentDescription = "Show ${day.label}"
                setOnClickListener {
                    selected = i
                    renderTabs()
                    renderTimeline()
                }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = Pit.dp(this@RaceDetailActivity, 6f)
            })
        }
        tabs.visibility = if (days.size > 1) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------
    // TIMELINE
    // ------------------------------------------------------------------

    private fun renderTimeline() {
        timeline.removeAllViews()
        val day = days.getOrNull(selected) ?: return
        val now = System.currentTimeMillis()
        day.entries.forEachIndexed { i, e ->
            timeline.addView(sessionRow(e, now, isLast = i == day.entries.lastIndex))
        }
    }

    private fun sessionRow(e: Entry, now: Long, isLast: Boolean): View {
        val ctx = this
        val ts = e.timed
        val live = ts?.isLive(now) == true
        val done = ts?.isDone(now) == true
        val isRace = SessionRoles.isRace(e.session)

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            alpha = if (done) 0.45f else 1f
            isClickable = true
            isFocusable = true
            background = Pit.pressable(ctx, p.bg, 12f, Pit.ripple(p))
            setOnClickListener { editSession(e) }
        }

        // ---- rail: dot + line ----
        val rail = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val dotSize = Pit.dp(ctx, if (live) 14f else 12f)
        val dot = View(ctx).apply {
            background = when {
                live -> Pit.oval(p.accent)
                done -> Pit.oval(p.faint)
                else -> android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0)
                    setStroke(Pit.dp(ctx, 2f), if (e.session.let { SessionRoles.gridTag(race, it) } != null) p.accent else p.faint)
                }
            }
        }
        rail.addView(dot, LinearLayout.LayoutParams(dotSize, dotSize).apply { topMargin = Pit.dp(ctx, 4f) })
        if (!isLast) {
            rail.addView(View(ctx).apply { setBackgroundColor(p.surface2) },
                LinearLayout.LayoutParams(Pit.dp(ctx, 2f), 0, 1f).apply { topMargin = Pit.dp(ctx, 4f) })
        }
        row.addView(rail, LinearLayout.LayoutParams(Pit.dp(ctx, 16f), LinearLayout.LayoutParams.MATCH_PARENT))

        // ---- content ----
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Pit.dp(ctx, 14f), 0, 0, Pit.dp(ctx, if (isLast) 8f else 22f))
        }

        val status = when {
            ts == null -> "TIME TBA"
            live -> "● LIVE NOW · ${Format.left(now, ts.end).uppercase(Locale.ENGLISH)}"
            done -> "DONE"
            else -> Format.relative(now, ts.start).uppercase(Locale.ENGLISH)
        }
        content.addView(TextView(ctx).apply {
            text = status
            styled(Pit.bodyBold, 11f, if (live) p.accentText else p.muted, 0.14f)
        })

        content.addView(TextView(ctx).apply {
            text = e.session.longLabel(race)
            styled(Pit.display(ctx), if (isRace) 22f else 20f, p.text)
            fit(13f, if (isRace) 22f else 20f)
            setPadding(0, Pit.dp(ctx, 6f), 0, 0)
        })

        val timeText = (ts?.let { Format.clock(ctx, it.start) } ?: "TBA") +
                " · ${e.session.durationMinutes(race)} min"
        content.addView(TextView(ctx).apply {
            text = timeText
            styled(Pit.bodyMedium, 14f, p.soft)
            setPadding(0, Pit.dp(ctx, 6f), 0, 0)
        })

        SessionRoles.note(race, e.session).takeIf { it.isNotEmpty() }?.let { note ->
            content.addView(TextView(ctx).apply {
                text = note
                styled(Pit.body, 12f, p.muted)
                setPadding(0, Pit.dp(ctx, 4f), 0, 0)
            })
        }

        // Official time differs from the built-in schedule
        com.example.schedule.data.ScheduleSync.movedFrom(ctx, race, e.session)?.let { was ->
            content.addView(TextView(ctx).apply {
                text = "MOVED · WAS ${Format.dayShort(was)} ${Format.clock(ctx, was)}"
                styled(Pit.bodyBold, 11f, p.accentText, 0.08f)
                setPadding(0, Pit.dp(ctx, 4f), 0, 0)
            })
        }

        SessionRoles.gridTag(race, e.session)?.let { tag ->
            content.addView(TextView(ctx).apply {
                text = tag
                styled(Pit.bodyBold, 10f, p.accentText, 0.1f)
                background = Pit.rounded(ctx, 0, 5f, p.accent)
                setPadding(Pit.dp(ctx, 7f), Pit.dp(ctx, 3f), Pit.dp(ctx, 7f), Pit.dp(ctx, 3f))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Pit.dp(ctx, 8f)
            })
        }

        if (live && ts != null) {
            val total = (ts.end - ts.start).toFloat()
            val frac = ((now - ts.start) / total).coerceIn(0f, 1f)
            val bar = FrameLayout(ctx).apply { background = Pit.rounded(ctx, p.surface2, 2f) }
            val fill = View(ctx).apply { background = Pit.rounded(ctx, p.accent, 2f) }
            bar.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
            bar.post { fill.layoutParams.width = (bar.width * frac).toInt(); fill.requestLayout() }
            content.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Pit.dp(ctx, 4f)).apply {
                topMargin = Pit.dp(ctx, 10f)
            })
        }

        row.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // ---- bell ----
        if (!done && ts != null) {
            val on = NotificationPrefs.isSessionOn(ctx, race, e.session)
            val bell = ImageButton(ctx).apply {
                setImageDrawable(icon(if (on) R.drawable.ic_bell else R.drawable.ic_bell_off))
                imageTintList = ColorStateList.valueOf(if (on) p.text else p.faint)
                background = Pit.pressable(ctx, if (on) p.surface2 else p.surface, 22f, Pit.ripple(p))
                setPadding(Pit.dp(ctx, 12f), Pit.dp(ctx, 12f), Pit.dp(ctx, 12f), Pit.dp(ctx, 12f))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = "${if (on) "Turn off" else "Turn on"} alert for ${e.session.longLabel(race)}"
                setOnClickListener { toggle(e.session, !on) }
            }
            row.addView(bell, LinearLayout.LayoutParams(Pit.dp(ctx, 44f), Pit.dp(ctx, 44f)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = Pit.dp(ctx, 8f)
            })
        }

        return row
    }

    private fun toggle(session: Session, on: Boolean) {
        when {
            NotificationPermissionHelper.notificationsDisabled(this) ->
                Toast.makeText(this, "Notifications are blocked — allow them in Settings", Toast.LENGTH_LONG).show()
            !NotificationPrefs.isMotoGPEnabled(this) ->
                Toast.makeText(this, "Session alerts are off — turn them on in Settings", Toast.LENGTH_LONG).show()
        }
        NotificationPrefs.setSessionOn(this, race, session, on)
        NotificationScheduler.rescheduleAll(this)
        renderTimeline()
        renderAlertCount()
    }

    // ------------------------------------------------------------------
    // EDIT A SESSION'S TIME (saved as an imported schedule; Settings → Use built-in undoes it)
    // ------------------------------------------------------------------

    private fun editSession(e: Entry) {
        val name = e.session.longLabel(race).lowercase(Locale.ENGLISH)
            .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }
        val options = mutableListOf("Change time")
        if (e.timed != null) options += "Mark as TBA"
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Change time" -> pickTime(e)
                    "Mark as TBA" -> saveSession(e.session, e.session.copy(sessionTime = RaceJsonParser.TBA))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTime(e: Entry) {
        // Day stays the one on the open tab; TBA sessions start from that day's first session
        val base = e.timed?.start
            ?: days.getOrNull(selected)?.entries?.firstOrNull { it.timed != null }?.timed?.start
            ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = base }
        TimePickerDialog(this, { _, hour, minute ->
            val chosen = Calendar.getInstance().apply {
                timeInMillis = base
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val ist = toIst(chosen)
            if (ist == null) {
                Toast.makeText(this, "That time is outside the race weekend", Toast.LENGTH_LONG).show()
            } else {
                saveSession(e.session, e.session.copy(sessionTime = ist.first, dayOffset = ist.second))
            }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show()
    }

    /** Phone-time instant → ("01:30 PM", dayOffset) in the JSON's IST convention */
    private fun toIst(millis: Long): Pair<String, Int>? {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val p = race.race.split("-")
        val friday = Calendar.getInstance(ist).apply {
            clear(); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        }.timeInMillis
        val day = Calendar.getInstance(ist).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val offset = Math.round((day - friday) / 86_400_000.0).toInt()
        if (offset !in 0..6) return null
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply { timeZone = ist }
            .format(millis).uppercase(Locale.ENGLISH)
        return time to offset
    }

    private fun saveSession(old: Session, new: Session) {
        val key = NotificationScheduler.raceKey(race)
        val updated = RaceRepository.getRaces(this).map { r ->
            if (NotificationScheduler.raceKey(r) == key) r.copy(sessions = r.sessions.map { if (it == old) new else it }) else r
        }
        val app = applicationContext
        Thread {
            val ok = runCatching { RaceRepository.saveImported(app, updated) }.isSuccess
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!ok) {
                    Toast.makeText(this, "Couldn't save the change", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                SchedulerCoordinator.onScheduleChanged(app)
                reload()
                Toast.makeText(this, "Saved · Settings → Use built-in schedule undoes edits", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun reload() {
        val key = NotificationScheduler.raceKey(race)
        race = RaceRepository.getRaces(this).firstOrNull { NotificationScheduler.raceKey(it) == key } ?: return
        sessions = WeekendTimeline.build(race)
        days = buildDays()
        selected = selected.coerceIn(0, (days.size - 1).coerceAtLeast(0))
        renderTabs()
        renderTimeline()
        renderAlertCount()
        renderExtras()
    }

    // ------------------------------------------------------------------
    // TRACK WEATHER + CIRCUIT CARD
    // ------------------------------------------------------------------

    private fun renderExtras() {
        extras.removeAllViews()
        val now = System.currentTimeMillis()

        // ---- weather (race week only, from cache) ----
        if (Weather.inRaceWeek(race, now)) {
            sectionLabel("TRACK WEATHER")
            val wx = Weather.weekend(this, race)
            if (wx.isEmpty()) {
                extras.addView(TextView(this).apply {
                    text = "Forecast loads when you're online"
                    styled(Pit.body, 13f, p.muted)
                    setPadding(Pit.dp(context, 4f), 0, 0, Pit.dp(context, 18f))
                })
            } else {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                wx.forEachIndexed { i, d ->
                    val label = runCatching {
                        SimpleDateFormat("EEE", Locale.ENGLISH).format(dayFmt.parse(d.date)!!).uppercase(Locale.ENGLISH)
                    }.getOrDefault(d.date)
                    row.addView(weatherChip(label, d), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (i > 0) marginStart = Pit.dp(this@RaceDetailActivity, 8f)
                    })
                }
                extras.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = Pit.dp(this@RaceDetailActivity, 18f)
                })
            }
        }

        // ---- circuit ----
        val c = Circuits.forRace(this, race) ?: return
        sectionLabel("CIRCUIT")
        extras.addView(circuitCard(c, now), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Pit.dp(this@RaceDetailActivity, 8f)
        })
    }

    private fun sectionLabel(text: String) {
        extras.addView(TextView(this).apply {
            this.text = text
            styled(Pit.bodyBold, 11f, p.muted, 0.16f)
            setPadding(Pit.dp(context, 4f), 0, 0, Pit.dp(context, 8f))
        })
    }

    private fun weatherChip(day: String, d: com.example.schedule.weather.DayWeather): View {
        val ctx = this
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Pit.rounded(ctx, p.surface, 16f)
            setPadding(Pit.dp(ctx, 6f), Pit.dp(ctx, 10f), Pit.dp(ctx, 6f), Pit.dp(ctx, 10f))
            contentDescription = "$day ${d.label}, high ${d.tMax} degrees, low ${d.tMin}" +
                    (d.rainChance?.let { ", $it percent chance of rain" } ?: "")
        }
        box.addView(TextView(ctx).apply { text = day; styled(Pit.bodyBold, 11f, p.muted, 0.1f) })
        box.addView(ImageView(ctx).apply { setImageResource(d.icon) },
            LinearLayout.LayoutParams(Pit.dp(ctx, 30f), Pit.dp(ctx, 30f)).apply { topMargin = Pit.dp(ctx, 6f) })
        box.addView(TextView(ctx).apply {
            text = "${d.tMax}° / ${d.tMin}°"
            styled(Pit.bodyBold, 14f, p.text)
            setPadding(0, Pit.dp(ctx, 6f), 0, 0)
        })
        box.addView(TextView(ctx).apply {
            text = d.rainChance?.let { "$it% rain" } ?: d.label
            styled(Pit.body, 11f, if ((d.rainChance ?: 0) >= 50) 0xFF5AB0FF.toInt() else p.muted)
            setPadding(0, Pit.dp(ctx, 2f), 0, 0)
        })
        return box
    }

    private fun circuitCard(c: Circuit, now: Long): View {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = Pit.rounded(ctx, p.surface, 20f)
            setPadding(Pit.dp(ctx, 18f), Pit.dp(ctx, 16f), Pit.dp(ctx, 18f), Pit.dp(ctx, 16f))
        }
        card.addView(TextView(ctx).apply {
            text = c.name.uppercase(Locale.ENGLISH)
            styled(Pit.display(ctx), 17f, p.text, 0.02f)
            fit(11f, 17f)
        })
        c.opened?.let { year ->
            card.addView(TextView(ctx).apply {
                text = if (year > Calendar.getInstance().get(Calendar.YEAR)) "New for $year" else "Opened $year"
                styled(Pit.body, 12f, p.muted)
                setPadding(0, Pit.dp(ctx, 4f), 0, 0)
            })
        }

        val tz = TimeZone.getTimeZone(c.tz)
        val trackTime = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", Locale.ENGLISH)
            .apply { timeZone = tz }.format(now).uppercase(Locale.ENGLISH)
        val offsetMin = tz.getOffset(now) / 60_000
        val gmt = "GMT" + (if (offsetMin >= 0) "+" else "−") + (Math.abs(offsetMin) / 60) +
                (if (Math.abs(offsetMin) % 60 != 0) ":" + (Math.abs(offsetMin) % 60).toString().padStart(2, '0') else "")

        val corners = c.totalCorners?.let { total ->
            if (c.left != null && c.right != null) "$total · ${c.left}L ${c.right}R" else "$total"
        } ?: "—"

        val stats = listOf(
            "LENGTH" to (c.lengthKm?.let { String.format(Locale.ENGLISH, "%.3f km", it) } ?: "—"),
            "CORNERS" to corners,
            "LONGEST STRAIGHT" to (c.longestStraightM?.let { String.format(Locale.ENGLISH, "%,d m", it) } ?: "—"),
            "TRACK TIME" to "$trackTime  $gmt"
        )
        stats.chunked(2).forEach { pair ->
            val line = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Pit.dp(ctx, 14f), 0, 0)
            }
            pair.forEach { (label, value) ->
                val cell = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                cell.addView(TextView(ctx).apply { text = label; styled(Pit.bodyMedium, 10f, p.muted, 0.12f) })
                cell.addView(TextView(ctx).apply {
                    text = value
                    styled(Pit.display(ctx), 16f, p.text)
                    fit(10f, 16f)
                    setPadding(0, Pit.dp(ctx, 4f), 0, 0)
                })
                line.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            card.addView(line)
        }
        return card
    }

    private fun renderAlertCount() {
        val now = System.currentTimeMillis()
        val upcoming = sessions.filter { !it.isDone(now) }
        val on = upcoming.count { NotificationPrefs.isSessionOn(this, race, it.session) }
        val master = NotificationPrefs.isMotoGPEnabled(this) &&
                !NotificationPermissionHelper.notificationsDisabled(this)

        alertsChip.text = when {
            upcoming.isEmpty() -> "Finished"
            !master -> "Alerts off"
            else -> "$on of ${upcoming.size}"
        }
        alertsChip.styled(Pit.bodyBold, 12f, p.text)
        val bell = icon(if (master && on > 0) R.drawable.ic_bell else R.drawable.ic_bell_off)?.apply {
            setTint(p.text)
            setBounds(0, 0, Pit.dp(this@RaceDetailActivity, 14f), Pit.dp(this@RaceDetailActivity, 14f))
        }
        alertsChip.setCompoundDrawables(bell, null, null, null)
        alertsChip.contentDescription = "Alerts set for $on of ${upcoming.size} sessions"
    }

    private fun icon(res: Int): Drawable? = ContextCompat.getDrawable(this, res)?.mutate()
}
