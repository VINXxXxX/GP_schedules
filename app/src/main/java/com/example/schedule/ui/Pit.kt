package com.example.schedule.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/**
 * "Pit Wall" design tokens: carbon black, one hot red, condensed racing type.
 * Every screen reads its colors from here so dark / light stay in sync.
 */
data class PitPalette(
    val bg: Int,
    val surface: Int,
    val surface2: Int,
    val line: Int,
    val text: Int,
    val soft: Int,
    val muted: Int,
    val faint: Int,
    val accent: Int,
    /** Accent used for small text (lighter in dark mode so it stays readable) */
    val accentText: Int,
    /** Background behind the live dot / live rows */
    val liveTint: Int,
    val onAccent: Int
)

object Pit {

    val DARK = PitPalette(
        bg = 0xFF0B0B0D.toInt(),
        surface = 0xFF141417.toInt(),
        surface2 = 0xFF222227.toInt(),
        line = 0xFF1F1F24.toInt(),
        text = 0xFFFFFFFF.toInt(),
        soft = 0xFFD4D4D8.toInt(),
        muted = 0xFFA1A1AA.toInt(),
        faint = 0xFF6B6B73.toInt(),
        accent = 0xFFE5261B.toInt(),
        accentText = 0xFFFF6B60.toInt(),
        liveTint = 0xFF2A0E0B.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    val LIGHT = PitPalette(
        bg = 0xFFF4F4F6.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        surface2 = 0xFFE9E9EE.toInt(),
        line = 0xFFE4E4E8.toInt(),
        text = 0xFF0B0B0D.toInt(),
        soft = 0xFF3F3F46.toInt(),
        muted = 0xFF5B5B66.toInt(),
        faint = 0xFF8E8E98.toInt(),
        accent = 0xFFD61F14.toInt(),
        accentText = 0xFFC01B12.toInt(),
        liveTint = 0xFFFBE3E1.toInt(),
        onAccent = 0xFFFFFFFF.toInt()
    )

    fun palette(context: Context): PitPalette =
        if (ThemePrefs.isDark(context)) DARK else LIGHT

    // ---------------- FONTS ----------------

    @Volatile private var bold: Typeface? = null
    @Volatile private var regular: Typeface? = null

    /** Big condensed headings (city names, timers, tabs) */
    fun display(context: Context): Typeface {
        bold?.let { return it }
        val tf = try {
            Typeface.createFromAsset(context.assets, "fonts/racing_bold.ttf")
        } catch (_: Exception) {
            Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        bold = tf
        return tf
    }

    fun displayRegular(context: Context): Typeface {
        regular?.let { return it }
        val tf = try {
            Typeface.createFromAsset(context.assets, "fonts/racing_regular.ttf")
        } catch (_: Exception) {
            Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        regular = tf
        return tf
    }

    val body: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val bodyMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bodyBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    // ---------------- HELPERS ----------------

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    fun rounded(context: Context, color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color)
            if (strokeColor != null) setStroke(dp(context, 1f), strokeColor)
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    /** Rounded surface with a touch ripple */
    fun pressable(context: Context, color: Int, radiusDp: Float, rippleColor: Int, strokeColor: Int? = null): Drawable =
        RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            rounded(context, color, radiusDp, strokeColor),
            rounded(context, 0xFFFFFFFF.toInt(), radiusDp)
        )

    fun ripple(p: PitPalette): Int = (p.text and 0x00FFFFFF) or 0x22000000

    fun TextView.styled(tf: Typeface, sizeSp: Float, color: Int, spacing: Float = 0f) {
        typeface = tf
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        letterSpacing = spacing
        includeFontPadding = false
    }

    /**
     * One line that shrinks to fit instead of wrapping mid-word or ellipsizing
     * (the racing font is wide). Call after [styled]; [maxSp] is the normal size.
     */
    fun TextView.fit(minSp: Float, maxSp: Float) {
        maxLines = 1
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, minSp.toInt(), maxSp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP
        )
    }

    fun View.setPaddingDp(l: Float, t: Float, r: Float, b: Float) {
        setPadding(dp(context, l), dp(context, t), dp(context, r), dp(context, b))
    }
}
