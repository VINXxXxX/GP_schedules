package com.example.schedule

import androidx.transition.Fade
import androidx.transition.TransitionManager
import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.schedule.model.Race
import com.example.schedule.ui.ThemeColors
import com.example.schedule.ui.ThemePrefs
import com.example.schedule.utils.RaceDateFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
class RaceAdapter(private val races: List<Race>) : RecyclerView.Adapter<RaceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardRoot)!!
        val frontSide      = view.findViewById<View>(R.id.frontSide)!!
        val backSide       = view.findViewById<View>(R.id.backSide)!!
        val dateRange      = view.findViewById<TextView>(R.id.dateRange)!!
        val round          = view.findViewById<TextView>(R.id.round)!!
        val country        = view.findViewById<TextView>(R.id.country)!!
        val track          = view.findViewById<TextView>(R.id.track)!!

        val friHeader = view.findViewById<TextView>(R.id.friHeader)!!
        val satHeader = view.findViewById<TextView>(R.id.satHeader)!!
        val sunHeader = view.findViewById<TextView>(R.id.sunHeader)!!
        val fridayColumn   = view.findViewById<LinearLayout>(R.id.fridayColumn)!!
        val saturdayColumn = view.findViewById<LinearLayout>(R.id.saturdayColumn)!!
        val sundayColumn   = view.findViewById<LinearLayout>(R.id.sundayColumn)!!

        var isBackShown = false
    }

    private var racingBold: Typeface? = null
    private var racingRegular: Typeface? = null
    private fun formatLocationForCard(raw: String): String {

        val city = raw
            .substringBefore(",")
            .trim()
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .uppercase()

        val country = raw
            .substringAfter(",", "")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.uppercase()

        return if (country != null) {
            "$city\n$country"
        } else {
            city
        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_race, parent, false)

        try {
            racingBold = Typeface.createFromAsset(parent.context.assets, "fonts/racing_bold.ttf")
            racingRegular = Typeface.createFromAsset(parent.context.assets, "fonts/racing_regular.ttf")
        } catch (_: Exception) {
            racingBold = Typeface.DEFAULT_BOLD
            racingRegular = Typeface.DEFAULT
        }

        return ViewHolder(view)
    }


    @SuppressLint("SetTextI18n", "UseKtx")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dark = ThemePrefs.isDark(holder.itemView.context)

// Card background
        holder.cardRoot.setCardBackgroundColor(
            ThemeColors.cardBg(dark)
        )

// Back side background
        holder.backSide.setBackgroundColor(
            ThemeColors.cardBg(dark)
        )

// Front text
        holder.dateRange.setTextColor(ThemeColors.textSecondary(dark))
        holder.round.setTextColor(ThemeColors.textPrimary(dark))
        holder.country.setTextColor(ThemeColors.textPrimary(dark))
        holder.track.setTextColor(ThemeColors.textMuted(dark))

// Back headers (FRI / SAT / SUN)
        racingBold?.let { bold ->
            holder.friHeader.typeface = bold
            holder.satHeader.typeface = bold
            holder.sunHeader.typeface = bold
        }

        holder.friHeader.setTextColor(ThemeColors.textPrimary(dark))
        holder.satHeader.setTextColor(ThemeColors.textPrimary(dark))
        holder.sunHeader.setTextColor(ThemeColors.textPrimary(dark))



        val race = races[position]
        val isMotoGP = race.category.equals("MotoGP", ignoreCase = true)

        holder.isBackShown = false
        holder.frontSide.visibility = View.VISIBLE
        holder.backSide.visibility = View.INVISIBLE

        holder.frontSide.visibility = View.VISIBLE
        holder.backSide.visibility = View.INVISIBLE
        holder.fridayColumn.removeAllViews()
        holder.saturdayColumn.removeAllViews()
        holder.sundayColumn.removeAllViews()

        // Date on front — "1 FEB"
        val raceDateStr = race.race
        val parts = raceDateStr.split("-")
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val monthName = when (month) {
            1 -> "JAN"
            2 -> "FEB"
            3 -> "MAR"
            4 -> "APR"
            5 -> "MAY"
            6 -> "JUN"
            7 -> "JUL"
            8 -> "AUG"
            9 -> "SEP"
            10 -> "OCT"
            11 -> "NOV"
            12 -> "DEC"
            else -> "???"
        }
        val friday = Calendar.getInstance().apply {
            val p = race.race.split("-")
            set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        holder.dateRange.text = RaceDateFormatter.formatWeekend(friday)
        // Text
        holder.round.text = race.round.toString()
        holder.country.text = formatLocationForCard(race.location)
        holder.track.text = race.track.uppercase()

        // Apply racing fonts
        racingBold?.let {
            holder.country.typeface = it
            holder.track.typeface = it
            holder.dateRange.typeface = it
        }
        racingRegular?.let { holder.round.typeface = it }


        // FLIP + SESSIONS
        holder.cardRoot.setOnClickListener {

            TransitionManager.beginDelayedTransition(
                holder.cardRoot,
                Fade().apply { duration = 180 }
            )

            holder.isBackShown = !holder.isBackShown

            holder.frontSide.visibility =
                if (holder.isBackShown) View.INVISIBLE else View.VISIBLE

            holder.backSide.visibility =
                if (holder.isBackShown) View.VISIBLE else View.INVISIBLE



        // Build sessions ONLY once
            if (holder.isBackShown && holder.fridayColumn.childCount == 0) {
                race.sessions.forEach { s ->

                    val lowerName = s.sessionName.lowercase()
                    val displayName = when (lowerName) {
                        "fp1" -> "FP1"
                        "fp2" ->
                            if (isMotoGP) "PR" else "FP2"

                        "fp3" ->
                            if (isMotoGP) "FP2" else "FP3"
                        "q1", "qualifying1" -> "Q1"
                        "q2", "qualifying2" -> "Q2"
                        "sprint", "spr" -> "SPR"
                        "race" -> "RACE"
                        "race1" -> "R1"
                        "race2" -> "R2"
                        "sp" -> "SP"
                        else -> s.sessionName.uppercase()
                    }

                    val convertedTime = try {
                        val input = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
                            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
                        }
                        val output = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        output.format(input.parse(s.sessionTime.trim())!!)
                    } catch (_: Exception) {
                        s.sessionTime
                    }

                    val tv = TextView(holder.itemView.context).apply {
                        text = "$displayName\n$convertedTime"
                        setTextColor(ThemeColors.textPrimary(dark))
                        textSize = if (dark) 15.5f else 17f
                        gravity = Gravity.CENTER
                        typeface = racingRegular
                        setPadding(0, 8, 0, 8)
                        letterSpacing = 0.08f
                    }

                    val dayCol = when (lowerName) {
                        "fp1", "fp2" -> holder.fridayColumn
                        "fp3", "q1", "q2", "sprint", "sp", "race1" -> holder.saturdayColumn
                        "race", "spr", "race2" -> holder.sundayColumn
                        else -> holder.sundayColumn
                    }
                    dayCol.addView(tv)
                }
            }

        }
    }

    override fun getItemCount() = races.size
}