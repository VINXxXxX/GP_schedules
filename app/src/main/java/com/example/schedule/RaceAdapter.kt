package com.example.schedule

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.schedule.model.Race

class RaceAdapter(private val races: List<Race>) : RecyclerView.Adapter<RaceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot       = view.findViewById<View>(R.id.cardRoot)!!
        val frontSide      = view.findViewById<View>(R.id.frontSide)!!
        val backSide       = view.findViewById<View>(R.id.backSide)!!
        val dateRange      = view.findViewById<TextView>(R.id.dateRange)!!
        val round          = view.findViewById<TextView>(R.id.round)!!
        val country        = view.findViewById<TextView>(R.id.country)!!
        val track          = view.findViewById<TextView>(R.id.track)!!
        val fridayColumn   = view.findViewById<LinearLayout>(R.id.fridayColumn)!!
        val saturdayColumn = view.findViewById<LinearLayout>(R.id.saturdayColumn)!!
        val sundayColumn   = view.findViewById<LinearLayout>(R.id.sundayColumn)!!
    }

    private var racingBold: Typeface? = null
    private var racingRegular: Typeface? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_race, parent, false)
        racingBold = Typeface.createFromAsset(parent.context.assets, "fonts/racing_bold.ttf")
        racingRegular = Typeface.createFromAsset(parent.context.assets, "fonts/racing_regular.ttf")
        return ViewHolder(view)
    }

    @SuppressLint("SetTextI18n", "UseKtx")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val race = races[position]
        var isBackShown = false

        holder.frontSide.isVisible = true
        holder.backSide.isVisible = false
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
        holder.dateRange.text = "$day $monthName"

        // Text
        holder.round.text = race.round.toString()
        holder.country.text = race.location.uppercase()
        holder.track.text = race.track.uppercase()

        // Apply racing fonts
        racingBold?.let {
            holder.country.typeface = it
            holder.track.typeface = it
            holder.dateRange.typeface = it
        }
        racingRegular?.let { holder.round.typeface = it }

        // FLIP + SESSIONS
        holder.cardRoot.setOnClickListener { // <-- Fixed: added ( )
            if (isBackShown) {
                flipCard(holder.backSide, holder.frontSide)
            } else {
                flipCard(holder.frontSide, holder.backSide)

                if (holder.fridayColumn.childCount == 0) {
                    race.sessions.forEach { s ->
                        val lowerName = s.sessionName.lowercase()
                        val displayName = when (lowerName) {
                            "fp1" -> "FP1"
                            "fp2" -> "FP2"
                            "fp3" -> "FP3"
                            "q1", "qualifying1" -> "Q1"
                            "q2", "qualifying2" -> "Q2"
                            "sprint"-> "SPRINT"
                            "spr"-> "SPR"
                            "race" -> "RACE"
                            "race1" -> "R1"
                            "race2" -> "R2"
                            "sp" -> "SP"
                            else -> s.sessionName.uppercase()
                        }

                        val tv = TextView(holder.itemView.context).apply {
                            text = "$displayName\n${s.sessionTime}"
                            setTextColor(-1)
                            textSize = 13f
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
            isBackShown = !isBackShown
        }
    }

    private fun flipCard(fromView: View, toView: View) {
        val scale = fromView.resources.displayMetrics.density
        fromView.cameraDistance = 8000 * scale
        toView.cameraDistance = 8000 * scale

        val animOut = ObjectAnimator.ofFloat(fromView, "rotationY", 0f, 90f)
        val animIn = ObjectAnimator.ofFloat(toView, "rotationY", -90f, 0f)

        animOut.duration = 300
        animIn.duration = 300

        animOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                fromView.isVisible = false
                toView.isVisible = true
                animIn.start()
            }
        })
        animOut.start()
    }

    override fun getItemCount() = races.size
}