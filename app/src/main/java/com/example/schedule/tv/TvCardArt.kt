package com.example.schedule.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.schedule.data.TimedSession
import com.example.schedule.model.Race
import com.example.schedule.tv.TvSupport.State
import com.example.schedule.ui.Format
import com.example.schedule.ui.Pit
import java.util.Locale

/**
 * 16:9 card image for one session in the TV channel row (Pit Wall look):
 *
 *   ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀  red bar (grey when done)
 *   R19 · SEPANG                  19   ← round watermark
 *   FP1
 *   FRI  8:15 AM
 *   [● LIVE]  FREE PRACTICE 1
 */
object TvCardArt {

    const val WIDTH = 640
    const val HEIGHT = 360

    private const val BG = 0xFF0B0B0D.toInt()
    private const val BG_LIVE = 0xFF2A0E0B.toInt()
    private const val WATERMARK = 0xFF17171A.toInt()
    private const val WATERMARK_LIVE = 0xFF3D1611.toInt()
    private const val RED = 0xFFE5261B.toInt()
    private const val RED_TEXT = 0xFFFF6B60.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SOFT = 0xFFE8E8EC.toInt()
    private const val MUTED = 0xFFA1A1AA.toInt()
    private const val FAINT = 0xFF6B6B73.toInt()
    private const val DONE_BAR = 0xFF3A3A40.toInt()
    private const val DONE_PILL = 0xFF26262B.toInt()

    /** Everything drawn on the card; equal content → same cached file */
    data class Content(
        val round: String,
        val header: String,
        val label: String,
        val longLabel: String,
        val dayTime: String,
        val state: State,
        val isRace: Boolean
    )

    fun content(context: Context, race: Race, s: TimedSession, state: State, now: Long): Content {
        val day = if (Format.calendarDays(now, s.start) == 0) "TODAY" else Format.dayShort(s.start)
        return Content(
            round = race.round.toString().padStart(2, '0'),
            header = "${TvSupport.roundText(race)} · ${Format.city(race)}",
            label = TvSupport.label(s),
            longLabel = s.longLabel.uppercase(Locale.ENGLISH),
            dayTime = "$day  ${Format.clock(context, s.start)}",
            state = state,
            isRace = TvSupport.isRace(s)
        )
    }

    fun render(context: Context, c: Content): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val display = Pit.display(context)
        val displayRegular = Pit.displayRegular(context)
        val done = c.state == State.DONE
        val live = c.state == State.LIVE

        canvas.drawColor(if (live) BG_LIVE else BG)

        // Round number watermark, bottom right
        paint(display, 300f, if (live) WATERMARK_LIVE else WATERMARK).apply {
            textAlign = Paint.Align.RIGHT
            canvas.drawText(c.round, WIDTH - 18f, HEIGHT - 24f, this)
        }

        // Top bar
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 10f, Paint().apply { color = if (done) DONE_BAR else RED })

        // Header: "R19 · SEPANG"
        canvas.drawText(
            c.header, 40f, 68f,
            fitted(paint(displayRegular, 30f, if (done) FAINT else MUTED), c.header, WIDTH - 80f)
        )

        // Big session label
        val labelColor = when {
            done -> FAINT
            live -> WHITE
            c.isRace -> RED_TEXT
            else -> WHITE
        }
        canvas.drawText(c.label, 36f, 206f, fitted(paint(display, 128f, labelColor), c.label, WIDTH - 72f))

        // Day + local time
        canvas.drawText(
            c.dayTime, 40f, 264f,
            fitted(paint(Typeface.DEFAULT_BOLD, 42f, if (done) FAINT else SOFT), c.dayTime, WIDTH - 80f)
        )

        // Status pill + long name
        var x = 40f
        val pillTop = 294f
        val pillBottom = 334f
        val pillText = when (c.state) {
            State.LIVE -> "LIVE"
            State.NEXT -> "NEXT"
            State.DONE -> "DONE"
            State.UPCOMING -> null
        }
        if (pillText != null) {
            val textPaint = paint(Typeface.DEFAULT_BOLD, 24f, if (c.state == State.NEXT) BG else if (done) FAINT else WHITE)
            val dot = if (live) 22f else 0f
            val w = textPaint.measureText(pillText) + 32f + dot
            val bg = when (c.state) {
                State.LIVE -> RED
                State.NEXT -> WHITE
                else -> DONE_PILL
            }
            canvas.drawRoundRect(RectF(x, pillTop, x + w, pillBottom), 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
            if (live) {
                canvas.drawCircle(x + 22f, (pillTop + pillBottom) / 2f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
            }
            canvas.drawText(pillText, x + 16f + dot, pillBottom - 12f, textPaint)
            x += w + 16f
        }
        canvas.drawText(
            c.longLabel, x, pillBottom - 12f,
            fitted(paint(displayRegular, 24f, if (done) FAINT else MUTED), c.longLabel, WIDTH - 40f - x)
        )

        return bmp
    }

    /** Square channel logo: red tile with "GP" */
    fun logo(context: Context): Bitmap {
        val size = 160
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 28f, 28f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
        paint(Pit.display(context), 76f, WHITE).apply {
            textAlign = Paint.Align.CENTER
            val y = size / 2f - (descent() + ascent()) / 2f
            canvas.drawText("GP", size / 2f, y, this)
        }
        return bmp
    }

    private fun paint(tf: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size
        this.color = color
    }

    /** Shrinks the text size until [text] fits in [maxWidth] */
    private fun fitted(p: Paint, text: String, maxWidth: Float): Paint {
        if (maxWidth <= 0f) return p
        while (p.textSize > 12f && p.measureText(text) > maxWidth) p.textSize -= 2f
        return p
    }
}
