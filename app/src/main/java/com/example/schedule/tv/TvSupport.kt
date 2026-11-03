package com.example.schedule.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.example.schedule.data.SessionCatalog
import com.example.schedule.data.TimedSession
import com.example.schedule.data.WeekendSnapshot
import com.example.schedule.model.Race

/** Shared bits for everything on the TV: device check, session states, labels. */
object TvSupport {

    /** Android TV / Google TV (leanback), or anything running in TV UI mode. */
    fun isTv(context: Context): Boolean {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    enum class State { DONE, LIVE, NEXT, UPCOMING }

    fun state(s: TimedSession, snap: WeekendSnapshot, now: Long): State = when {
        s.isLive(now) -> State.LIVE
        s.isDone(now) -> State.DONE
        s == snap.next -> State.NEXT
        else -> State.UPCOMING
    }

    /** Tile / card label: "GP" reads better than "RACE" */
    fun label(s: TimedSession): String =
        if (SessionCatalog.canonical(s.session.sessionName) == "race") "GP" else s.shortLabel

    /** Sprint and Grand Prix */
    fun isRace(s: TimedSession): Boolean =
        SessionCatalog.canonical(s.session.sessionName) in setOf("sprint", "race")

    /** Stable id for one session of one round (survives time edits) */
    fun sessionKey(race: Race, s: TimedSession): String =
        "${race.race}|${race.slug}|${SessionCatalog.canonical(s.session.sessionName)}|${s.session.dayOffset}"

    fun roundText(race: Race): String = "R" + race.round.toString().padStart(2, '0')

    const val DAY_MS = 24L * 60 * 60 * 1000
}
