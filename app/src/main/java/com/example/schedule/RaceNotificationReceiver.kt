package com.example.schedule

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.schedule.data.RaceRepository
import com.example.schedule.data.SessionCatalog
import com.example.schedule.data.SessionRoles
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendTimeline
import com.example.schedule.data.durationMinutes
import com.example.schedule.data.longLabel
import com.example.schedule.model.Race
import com.example.schedule.ui.Format
import com.example.schedule.ui.Pit
import java.util.Locale

/**
 * Builds the notification when its alarm fires, from the current schedule.
 * Custom layouts (notif_*.xml) inside the system frame; no action buttons —
 * tapping opens that round's session page.
 */
class RaceNotificationReceiver : BroadcastReceiver() {

    companion object {
        /**
         * Settings → "Send test notifications": shows a reminder, the day's sessions and the
         * race-week schedule right now, using the next round's real sessions.
         */
        fun sendTest(context: Context): Boolean {
            val now = System.currentTimeMillis()
            val snap = WeekendTimeline.snapshot(RaceRepository.getRaces(context), now) ?: return false
            val race = snap.race
            val sessions = snap.sessions
            if (sessions.isEmpty()) return false
            val r = RaceNotificationReceiver()
            val lead = NotificationPrefs.leadMinutes(context) * 60_000L

            val first = sessions[0]
            val len1 = first.end - first.start
            r.postReminder(context, race, sessions, first.copy(start = now + lead, end = now + lead + len1), now, test = true)

            // Today's-sessions notification for the first weekend day
            r.postDay(context, race, sessions, NotificationScheduler.dayKey(sessions[0].start), test = true)

            r.postPreview(context, race, sessions, test = true)
            return true
        }

        private const val TEST_ID = 990_000
        private const val RED = 0xFFE5372C.toInt()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (NotificationPermissionHelper.notificationsDisabled(context)) return
        if (!NotificationPrefs.isMotoGPEnabled(context)) return

        val type = intent.getIntExtra(NotificationScheduler.EXTRA_TYPE, 0)
        val key = intent.getStringExtra(NotificationScheduler.EXTRA_RACE) ?: return
        val race = RaceRepository.getRaces(context)
            .firstOrNull { NotificationScheduler.raceKey(it) == key } ?: return
        val sessions = WeekendTimeline.build(race)
        val now = System.currentTimeMillis()

        when (type) {
            NotificationScheduler.TYPE_WEEK -> postPreview(context, race, sessions)
            NotificationScheduler.TYPE_DAY -> {
                val day = intent.getStringExtra(NotificationScheduler.EXTRA_DAY) ?: return
                postDay(context, race, sessions, day)
            }
            NotificationScheduler.TYPE_CLEAR -> {
                val name = intent.getStringExtra(NotificationScheduler.EXTRA_SESSION) ?: return
                sessions.firstOrNull {
                    SessionCatalog.canonical(it.session.sessionName) == SessionCatalog.canonical(name)
                }?.let { NotificationManagerCompat.from(context).cancel(NotificationScheduler.notificationId(race, it)) }
            }
            NotificationScheduler.TYPE_REMINDER -> {
                val name = intent.getStringExtra(NotificationScheduler.EXTRA_SESSION) ?: return
                val ts = sessions.firstOrNull {
                    SessionCatalog.canonical(it.session.sessionName) == SessionCatalog.canonical(name)
                } ?: return
                if (!NotificationPrefs.isSessionOn(context, race, ts.session)) return
                if (now < ts.start) postReminder(context, race, sessions, ts, now)
            }
        }
    }

    // ------------------------------------------------------------------
    // REMINDER
    // ------------------------------------------------------------------

    private fun postReminder(context: Context, race: Race, all: List<TimedSession>, ts: TimedSession, now: Long, test: Boolean = false) {
        val mins = ((ts.start - now + 30_000L) / 60_000L).coerceAtLeast(1)
        val inText = if (mins >= 60) "${mins / 60} H ${mins % 60} MIN" else "$mins MIN"
        val title = longName(race, ts)
        val sub = "${city(race)} · ${Format.clock(context, ts.start)} · ${ts.session.durationMinutes(race)} min"
        val label = "STARTS IN $inText"
        val note = SessionRoles.note(race, ts.session)

        val small = sessionViews(context, R.layout.notif_session, label, title, sub, "STARTS IN", ts.start - now)
        val big = sessionViews(context, R.layout.notif_session_big, label, title, sub, "STARTS IN", ts.start - now)
        big.setTextViewText(R.id.n_note, note)
        big.setViewVisibility(R.id.n_note, if (note.isEmpty()) View.GONE else View.VISIBLE)
        big.setTextViewText(R.id.n_day, dayLine(context, race, all, ts))

        val b = base(context, race, NotificationChannels.REMINDERS)
            .setContentTitle("$title starts in ${inText.lowercase(Locale.ENGLISH)}")
            .setContentText(sub)
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setCustomHeadsUpContentView(small)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // Gone the moment the session starts, so the countdown never goes below 0:00
            .setTimeoutAfter((ts.start - now).coerceAtLeast(1_000L))
            .setAutoCancel(true)

        notify(context, if (test) TEST_ID + 1 else NotificationScheduler.notificationId(race, ts), b)
    }

    // ------------------------------------------------------------------
    // TODAY'S SESSIONS (each weekend day)
    // ------------------------------------------------------------------

    private fun postDay(context: Context, race: Race, all: List<TimedSession>, dayKey: String, test: Boolean = false) {
        if (!test && !NotificationPrefs.dailySchedule(context)) return
        val today = all.filter { NotificationScheduler.dayKey(it.start) == dayKey }
        if (today.isEmpty()) return

        val first = today.first()
        val dayName = java.text.SimpleDateFormat("EEEE d MMM", Locale.ENGLISH).format(first.start).uppercase(Locale.ENGLISH)
        val hasGp = today.any { SessionCatalog.canonical(it.session.sessionName) == "race" }
        val label = if (hasGp) "RACE DAY · $dayName" else "TODAY · $dayName"
        val title = "${city(race)} · ${today.size} session${if (today.size == 1) "" else "s"}"
        val sub = today.joinToString(" · ") { "${shortName(race, it)} ${Format.clock(context, it.start)}" }

        fun fill(v: RemoteViews) {
            v.setTextViewText(R.id.n_label, label)
            v.setTextViewText(R.id.n_title, title)
            v.setTextViewText(R.id.n_sub, sub)
        }
        val small = RemoteViews(context.packageName, R.layout.notif_preview).also { fill(it) }
        val big = RemoteViews(context.packageName, R.layout.notif_preview_big).also { fill(it) }
        big.setTextViewText(R.id.n_sub, "Round ${race.round} · ${race.track}")

        // One line per session: name, time, what it decides
        val sb = SpannableStringBuilder()
        today.forEachIndexed { i, s ->
            if (i > 0) sb.append("\n")
            sb.styled(longName(race, s), null, bold = true)
            sb.append("  ").append(Format.clock(context, s.start))
            SessionRoles.note(race, s.session).takeIf { it.isNotEmpty() }?.let { sb.styled("  ·  $it", null) }
        }
        big.setTextViewText(R.id.n_day_1, sb)
        big.setViewVisibility(R.id.n_day_1, View.VISIBLE)
        listOf(R.id.n_day_2, R.id.n_day_3, R.id.n_day_4).forEach { big.setViewVisibility(it, View.GONE) }

        val b = base(context, race, NotificationChannels.DAILY)
            .setContentTitle("$label · ${city(race)}")
            .setContentText(sub)
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(if (test) 0L else (today.last().end - System.currentTimeMillis()).coerceAtLeast(60_000L))

        notify(context, if (test) TEST_ID + 4 else NotificationScheduler.dayId(race, 0) + (today.first().session.dayOffset), b)
    }

    // ------------------------------------------------------------------
    // WEEKEND PREVIEW
    // ------------------------------------------------------------------

    private fun postPreview(context: Context, race: Race, sessions: List<TimedSession>, test: Boolean = false) {
        if (!test && !NotificationPrefs.weekendPreview(context)) return

        val range = Format.weekendRange(race, sessions)
        val label = "RACE WEEK · ${range}"
        val title = "${city(race)} · Round ${race.round}"
        val first = sessions.firstOrNull()
        val sub = first?.let { "First: ${shortName(race, it)} ${Format.dayShort(it.start).cap()} ${Format.clock(context, it.start)}" }
            ?: race.track

        fun fill(v: RemoteViews) {
            v.setTextViewText(R.id.n_label, label)
            v.setTextViewText(R.id.n_title, title)
            v.setTextViewText(R.id.n_sub, sub)
        }

        val small = RemoteViews(context.packageName, R.layout.notif_preview).also { fill(it) }
        val big = RemoteViews(context.packageName, R.layout.notif_preview_big).also { fill(it) }
        big.setTextViewText(R.id.n_sub, race.track)

        // One column per local day
        val days = sessions.groupBy { Format.dayLabel(it.start) }.entries.toList()
        val ids = intArrayOf(R.id.n_day_1, R.id.n_day_2, R.id.n_day_3, R.id.n_day_4)
        ids.forEachIndexed { i, id ->
            val day = days.getOrNull(i)
            if (day == null) {
                big.setViewVisibility(id, if (i < 3) View.INVISIBLE else View.GONE)
            } else {
                val sb = SpannableStringBuilder()
                sb.styled(day.key, RED, bold = true)
                day.value.forEach { s ->
                    sb.append("\n")
                    sb.styled(shortName(race, s), null, bold = SessionRoles.isRace(s.session))
                    sb.append(" ").append(Format.clock(context, s.start))
                }
                big.setTextViewText(id, sb)
                big.setViewVisibility(id, View.VISIBLE)
            }
        }

        val tba = race.sessions.filter { s -> sessions.none { it.session == s } }
        if (tba.isNotEmpty()) {
            big.setTextViewText(R.id.n_tba, tba.joinToString(" · ") { it.longLabel(race).titleCase() } + " — time TBA")
            big.setViewVisibility(R.id.n_tba, View.VISIBLE)
        }

        val b = base(context, race, NotificationChannels.PREVIEW)
            .setContentTitle("Race week · $title")
            .setContentText(sub)
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notify(context, if (test) TEST_ID + 3 else NotificationScheduler.previewId(race), b)
    }

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    private fun sessionViews(
        context: Context, layout: Int,
        label: String, title: String, sub: String,
        timerLabel: String, untilMillis: Long
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.n_label, label)
        setTextViewText(R.id.n_title, title)
        setTextViewText(R.id.n_sub, sub)
        setTextViewText(R.id.n_timer_label, timerLabel)
        setChronometerCountDown(R.id.n_timer, true)
        setChronometer(R.id.n_timer, SystemClock.elapsedRealtime() + untilMillis.coerceAtLeast(0), null, true)
    }

    /** "Fri  FP1 9:15 AM · FP2 1:30 PM · Sprint Quali TBA" — the session's own day, current one bold */
    private fun dayLine(context: Context, race: Race, all: List<TimedSession>, ts: TimedSession): CharSequence {
        val day = Format.dayLabel(ts.start)
        val same = all.filter { Format.dayLabel(it.start) == day }
        val sb = SpannableStringBuilder()
        sb.styled(Format.dayShort(ts.start).cap() + "   ", RED, bold = true)
        same.forEachIndexed { i, s ->
            if (i > 0) sb.append("  ·  ")
            val current = s.session == ts.session
            sb.styled("${shortName(race, s)} ${Format.clock(context, s.start)}", null, bold = current)
        }
        race.sessions.filter { s -> all.none { it.session == s } && s.dayOffset == ts.session.dayOffset }
            .forEach { sb.append("  ·  ").append("${it.longLabel(race).titleCase()} TBA") }
        return sb
    }

    private fun base(context: Context, race: Race, channel: String): NotificationCompat.Builder {
        val open = Intent(context, RaceDetailActivity::class.java)
            .putExtra(RaceDetailActivity.EXTRA_RACE, NotificationScheduler.raceKey(race))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, race.round, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Pit.DARK.accent)
            .setContentIntent(pi)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, b: NotificationCompat.Builder) {
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    /** "Free Practice 1", "Sprint Qualifying", "Grand Prix" */
    private fun longName(race: Race, ts: TimedSession): String =
        when (SessionCatalog.canonical(ts.session.sessionName)) {
            "sq" -> "SQ"
            "q1" -> "Qualifying 1"
            "q2" -> "Qualifying 2"
            else -> ts.longLabel.titleCase()
        }

    /** "FP1", "Q2", "Sprint", "GP" */
    private fun shortName(race: Race, ts: TimedSession): String =
        when (SessionCatalog.canonical(ts.session.sessionName)) {
            "sprint" -> "Sprint"
            "race" -> "GP"
            else -> ts.shortLabel
        }

    private fun city(race: Race) = Format.city(race).titleCase()

    private fun String.cap() = lowercase(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }

    private fun String.titleCase(): String =
        lowercase(Locale.ENGLISH).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }

    private fun SpannableStringBuilder.styled(text: String, color: Int?, bold: Boolean = false) {
        val start = length
        append(text)
        if (color != null) setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold) setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
