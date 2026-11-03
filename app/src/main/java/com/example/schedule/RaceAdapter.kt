package com.example.schedule

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.season
import com.example.schedule.model.Race
import com.example.schedule.ui.Format
import com.example.schedule.ui.Pit
import com.example.schedule.ui.Pit.fit
import com.example.schedule.ui.Pit.styled
import com.example.schedule.ui.PitPalette

/**
 * Home screen: season header, a big "next up" card with a live countdown,
 * then upcoming rounds and finished rounds. Tap any round for its sessions.
 */
class RaceAdapter(
    private val context: Context,
    private val races: List<Race>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        object Header : Item()
        object Hero : Item()
        data class Section(val label: String) : Item()
        data class Row(val index: Int, val done: Boolean) : Item()
        object Empty : Item()
    }

    private val p: PitPalette = Pit.palette(context)
    private val timelines: List<List<TimedSession>> = races.map { WeekendTimeline.build(it) }

    private var snap: WeekendSnapshot? = null
    private var items: List<Item> = emptyList()
    private var heroHolder: HeroHolder? = null
    private var builtFor: Int = -2

    init {
        refresh(System.currentTimeMillis())
    }

    /** Recomputes the snapshot; rebuilds the list only when the featured round changes. */
    @SuppressLint("NotifyDataSetChanged")
    fun refresh(now: Long) {
        snap = WeekendTimeline.snapshot(races, now)
        val featured = snap?.let { it.position - 1 } ?: -1
        if (featured != builtFor) {
            builtFor = featured
            items = buildItems(featured)
            notifyDataSetChanged()
        }
    }

    /** Weather arrived: redraw the hero */
    fun weatherChanged() {
        val i = items.indexOf(Item.Hero)
        if (i >= 0) notifyItemChanged(i)
    }

    /** Called every second by the fragment. */
    fun tick(now: Long) {
        heroHolder?.let { bindHeroTime(it, now) }
    }

    private fun buildItems(featured: Int): List<Item> {
        if (races.isEmpty()) return listOf(Item.Empty)
        val list = mutableListOf<Item>(Item.Header, Item.Hero)
        val upcoming = (featured + 1 until races.size).toList()
        val done = (0 until featured).toList()
        if (upcoming.isNotEmpty()) {
            list += Item.Section("UPCOMING")
            upcoming.forEach { list += Item.Row(it, false) }
        }
        if (done.isNotEmpty()) {
            list += Item.Section("COMPLETED")
            done.reversed().forEach { list += Item.Row(it, true) }
        }
        return list
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = when (items[position]) {
        Item.Header -> 0
        Item.Hero -> 1
        is Item.Section -> 2
        is Item.Row -> 3
        Item.Empty -> 4
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> HeaderHolder(inf.inflate(R.layout.item_home_header, parent, false))
            1 -> HeroHolder(inf.inflate(R.layout.item_race_hero, parent, false))
            2 -> SectionHolder(inf.inflate(R.layout.item_section, parent, false))
            3 -> RowHolder(inf.inflate(R.layout.item_race_row, parent, false))
            else -> SectionHolder(inf.inflate(R.layout.item_section, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            Item.Header -> bindHeader(holder as HeaderHolder)
            Item.Hero -> bindHero(holder as HeroHolder)
            is Item.Section -> (holder as SectionHolder).label.apply {
                text = item.label
                styled(Pit.bodyMedium, 11f, p.muted, 0.16f)
            }
            is Item.Row -> bindRow(holder as RowHolder, item)
            Item.Empty -> (holder as SectionHolder).label.apply {
                text = "No schedule loaded.\nImport a season JSON in Settings."
                styled(Pit.body, 15f, p.muted)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder === heroHolder) heroHolder = null
        super.onViewRecycled(holder)
    }

    // ------------------------------------------------------------------
    // HEADER
    // ------------------------------------------------------------------

    private class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.homeTitle)
        val round: TextView = v.findViewById(R.id.homeRound)
        val segments: LinearLayout = v.findViewById(R.id.segments)
    }

    private fun bindHeader(h: HeaderHolder) {
        val s = snap ?: return
        val season = s.race.season()
        h.title.text = SpannableStringBuilder("MOTOGP ").apply {
            val start = length
            append(season.toString())
            setSpan(ForegroundColorSpan(p.accent), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        h.title.styled(Pit.display(context), 20f, p.text, 0.06f)
        h.round.text = if (s.seasonOver) "SEASON COMPLETE" else "ROUND ${s.position} OF ${s.totalRounds}"
        h.round.styled(Pit.bodyMedium, 12f, p.muted, 0.08f)

        h.segments.removeAllViews()
        val gap = Pit.dp(context, 3f)
        for (i in 0 until s.totalRounds) {
            val seg = View(context)
            val color = when {
                i < s.completedRounds -> p.soft
                i == s.position - 1 && !s.seasonOver -> p.accent
                else -> p.surface2
            }
            seg.background = Pit.rounded(context, color, 2f)
            h.segments.addView(seg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                if (i > 0) marginStart = gap
            })
        }
    }

    // ------------------------------------------------------------------
    // HERO
    // ------------------------------------------------------------------

    private class HeroHolder(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.heroCard)
        val watermark: TextView = v.findViewById(R.id.heroWatermark)
        val chip: TextView = v.findViewById(R.id.heroChip)
        val weather: TextView = v.findViewById(R.id.heroWeather)
        val city: TextView = v.findViewById(R.id.heroCity)
        val country: TextView = v.findViewById(R.id.heroCountry)
        val track: TextView = v.findViewById(R.id.heroTrack)
        val boxes: List<View> = listOf(R.id.cd1, R.id.cd2, R.id.cd3, R.id.cd4).map { v.findViewById(it) }
        val status: TextView = v.findViewById(R.id.heroStatus)
        val action: TextView = v.findViewById(R.id.heroAction)
    }

    private fun bindHero(h: HeroHolder) {
        heroHolder = h
        val s = snap ?: return
        val race = s.race

        h.card.background = Pit.pressable(context, p.surface, 28f, Pit.ripple(p))
        h.card.setOnClickListener { openRace(race) }

        h.watermark.text = race.round.toString().padStart(2, '0')
        h.watermark.styled(Pit.display(context), 130f, if (p == Pit.DARK) 0xFF1C1C20.toInt() else 0xFFEDEDF0.toInt())

        h.city.text = Format.city(race)
        h.city.styled(Pit.display(context), 44f, p.text, 0.01f)
        h.city.fit(24f, 44f)
        h.country.text = Format.country(race)
        h.country.styled(Pit.display(context), 20f, p.muted, 0.06f)
        h.country.fit(13f, 20f)
        h.country.visibility = if (h.country.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        h.track.text = race.track.uppercase()
        h.track.styled(Pit.bodyMedium, 12f, p.muted, 0.1f)

        h.boxes.forEachIndexed { i, box ->
            box.background = Pit.rounded(context, p.bg, 14f)
            (box.layoutParams as LinearLayout.LayoutParams).marginEnd = if (i == 3) 0 else Pit.dp(context, 8f)
            box.findViewById<TextView>(R.id.cdValue).apply {
                styled(Pit.display(context), 28f, if (i == 3) p.accent else p.text)
                fit(16f, 28f)
            }
            box.findViewById<TextView>(R.id.cdLabel).styled(Pit.bodyMedium, 10f, p.muted, 0.12f)
        }

        h.action.styled(Pit.bodyBold, 13f, p.accentText)

        // Track weather: race week only, from the daily cache
        val wx = com.example.schedule.weather.Weather.headline(context, race, System.currentTimeMillis())
        if (wx == null) {
            h.weather.visibility = View.GONE
        } else {
            h.weather.visibility = View.VISIBLE
            h.weather.text = "${wx.tMax}°" + (wx.rainChance?.let { " · $it% rain" } ?: "")
            h.weather.styled(Pit.bodyBold, 12f, p.text)
            h.weather.background = Pit.rounded(context, p.surface2, 6f)
            val icon = androidx.core.content.ContextCompat.getDrawable(context, wx.icon)?.apply {
                setBounds(0, 0, Pit.dp(context, 16f), Pit.dp(context, 16f))
            }
            h.weather.setCompoundDrawables(icon, null, null, null)
            h.weather.contentDescription = "Track weather ${wx.label}, ${wx.tMax} degrees"
        }
        bindHeroTime(h, System.currentTimeMillis())
    }

    private fun bindHeroTime(h: HeroHolder, now: Long) {
        val s = snap ?: return
        val race = s.race
        val range = Format.weekendRange(race, s.sessions)
        val live = s.sessions.firstOrNull { it.isLive(now) }
        val next = s.sessions.firstOrNull { it.start > now }

        // Chip
        val (chipText, chipBg, chipFg) = when {
            s.seasonOver -> Triple("SEASON COMPLETE", p.surface2, p.soft)
            live != null -> Triple("● LIVE · $range", p.accent, p.onAccent)
            s.isRaceWeekend(now) -> Triple("RACE WEEKEND · $range", p.accent, p.onAccent)
            else -> Triple("NEXT UP · $range", p.accent, p.onAccent)
        }
        h.chip.text = chipText
        h.chip.background = Pit.rounded(context, chipBg, 6f)
        h.chip.styled(Pit.bodyBold, 11f, chipFg, 0.14f)

        // Countdown target: live → its end, otherwise the next start
        val target = live?.end ?: next?.start
        val labels: List<String>
        val values: List<String>
        if (target == null) {
            labels = listOf("DAYS", "HRS", "MIN", "SEC")
            values = listOf("–", "–", "–", "–")
        } else {
            val total = ((target - now) / 1000L).coerceAtLeast(0)
            val d = total / 86_400
            val hr = (total % 86_400) / 3600
            val m = (total % 3600) / 60
            val sec = total % 60
            labels = listOf("DAYS", "HRS", "MIN", "SEC")
            values = listOf(d.toString(), hr.pad(), m.pad(), sec.pad())
        }
        h.boxes.forEachIndexed { i, box ->
            box.findViewById<TextView>(R.id.cdValue).text = values[i]
            box.findViewById<TextView>(R.id.cdLabel).text = labels[i]
        }

        // Status line
        h.status.styled(Pit.body, 13f, p.muted)
        h.status.text = SpannableStringBuilder().apply {
            when {
                s.seasonOver -> append("Import next season in Settings")
                live != null -> {
                    appendColored("● ${live.longLabel} ", p.accentText)
                    append(Format.left(now, live.end))
                }
                next != null -> {
                    append(if (next == s.sessions.firstOrNull()) "First session " else "Next ")
                    appendColored("${next.shortLabel} · ${Format.dayShort(next.start).cap()} ${Format.clock(context, next.start)}", p.text)
                }
                else -> append("Weekend finished")
            }
        }
    }

    // ------------------------------------------------------------------
    // ROWS
    // ------------------------------------------------------------------

    private class RowHolder(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.rowRoot)
        val round: TextView = v.findViewById(R.id.rowRound)
        val title: TextView = v.findViewById(R.id.rowTitle)
        val track: TextView = v.findViewById(R.id.rowTrack)
        val date: TextView = v.findViewById(R.id.rowDate)
    }

    private fun bindRow(h: RowHolder, item: Item.Row) {
        val race = races[item.index]
        h.root.background = Pit.pressable(context, p.surface, 16f, Pit.ripple(p))
        h.root.alpha = if (item.done) 0.55f else 1f
        h.root.setOnClickListener { openRace(race) }

        h.round.text = race.round.toString().padStart(2, '0')
        h.round.styled(Pit.display(context), 18f, p.faint)
        h.round.fit(12f, 18f)

        val country = Format.country(race)
        h.title.text = if (country.isNotEmpty()) "${Format.city(race)} · ${shortCountry(country)}" else Format.city(race)
        h.title.styled(Pit.display(context), 17f, p.text, 0.03f)
        h.title.fit(12f, 17f)
        h.track.text = race.track
        h.track.styled(Pit.body, 11f, p.muted)
        h.date.text = Format.weekendRange(race, timelines[item.index])
        h.date.styled(Pit.bodyMedium, 12f, p.soft)
    }

    private fun shortCountry(c: String) = when (c) {
        "UNITED KINGDOM", "GREAT BRITAIN" -> "GBR"
        "ARGENTINA" -> "ARG"
        "SAN MARINO" -> "SMR"
        "NETHERLANDS" -> "NED"
        else -> if (c.length > 12) c.take(3) else c
    }

    // ------------------------------------------------------------------

    private class SectionHolder(v: View) : RecyclerView.ViewHolder(v) {
        val label: TextView = v as TextView
    }

    private fun openRace(race: Race) {
        context.startActivity(
            Intent(context, RaceDetailActivity::class.java)
                .putExtra(RaceDetailActivity.EXTRA_RACE, NotificationScheduler.raceKey(race))
        )
    }

    private fun Long.pad() = toString().padStart(2, '0')
    private fun String.cap() = lowercase().replaceFirstChar { it.uppercase() }

    private fun SpannableStringBuilder.appendColored(text: String, color: Int) {
        val start = length
        append(text)
        setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
