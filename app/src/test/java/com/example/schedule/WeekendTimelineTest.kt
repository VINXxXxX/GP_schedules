package com.example.schedule

import com.example.schedule.data.RaceJsonParser
import com.example.schedule.data.SessionCatalog
import com.example.schedule.data.WeekendTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeekendTimelineTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private fun istMillis(s: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).apply { timeZone = ist }.parse(s)!!.time

    /** 2027-style weekend with Sprint Qualifying on Friday. */
    private val races = RaceJsonParser.parse(
        """
        { "races": [
          { "name": "Test GP", "location": "Testville, Testland", "round": 1, "race": "2027-03-05",
            "sessions": [
              { "sessionName": "fp1",          "sessionTime": "09:15 AM", "dayOffset": 0 },
              { "sessionName": "fp2",          "sessionTime": "01:30 PM", "dayOffset": 0 },
              { "sessionName": "Sprint_Quali", "sessionTime": "02:15 PM", "dayOffset": 0 },
              { "sessionName": "fp3",          "sessionTime": "08:40 AM", "dayOffset": 1 },
              { "sessionName": "q1",           "sessionTime": "09:20 AM", "dayOffset": 1 },
              { "sessionName": "q2",           "sessionTime": "09:45 AM", "dayOffset": 1 },
              { "sessionName": "sprint",       "sessionTime": "01:30 PM", "dayOffset": 1 },
              { "sessionName": "race",         "sessionTime": "01:30 PM", "dayOffset": 2 }
            ] },
          { "name": "Second GP", "location": "Two, Land", "round": 2, "race": "2027-03-12",
            "sessions": [ { "sessionName": "race", "sessionTime": "10:30 PM", "dayOffset": 2 } ] }
        ] }
        """.trimIndent()
    )

    @Test
    fun labelsFollowTheSeason() {
        assertEquals("PR", SessionCatalog.info("fp2", 2026).short)
        assertEquals("FP2", SessionCatalog.info("fp2", 2027).short)
        assertEquals("FP3", SessionCatalog.info("fp3", 2027).short)
        assertEquals("SQ", SessionCatalog.info("Sprint_Quali", 2027).short)
        assertEquals("sq", SessionCatalog.canonical("Sprint Qualifying"))
    }

    @Test
    fun sprintQualifyingIsParsedAndOrdered() {
        val labels = WeekendTimeline.build(races[0]).map { it.shortLabel }
        assertEquals(listOf("FP1", "FP2", "SQ", "FP3", "Q1", "Q2", "SPR", "RACE"), labels)
    }

    @Test
    fun liveAndNextAreDetected() {
        val during = istMillis("2027-03-05 14:20") // 5 min into SQ (15 min long)
        val snap = WeekendTimeline.snapshot(races, during, ist)!!

        assertEquals("SQ", snap.live?.shortLabel)
        assertEquals("FP3", snap.next?.shortLabel)
        assertEquals(1, snap.position)
        assertTrue(snap.isRaceWeekend(during))
        assertEquals(3, snap.days.size)
        assertEquals("FRI 05", snap.days[0].label)
        assertTrue(snap.days[0].isToday)
    }

    @Test
    fun switchesToNextRoundWhenWeekendEnds() {
        val afterRace = istMillis("2027-03-07 15:00") // GP 13:30 + 50 min is over
        val snap = WeekendTimeline.snapshot(races, afterRace, ist)!!

        assertEquals("Second GP", snap.race.name)
        assertEquals(1, snap.completedRounds)
        assertNull(snap.live)
    }

    @Test
    fun seasonOverKeepsLastRound() {
        val snap = WeekendTimeline.snapshot(races, istMillis("2027-12-01 00:00"), ist)!!
        assertTrue(snap.seasonOver)
        assertEquals("Second GP", snap.race.name)
    }

    @Test
    fun nextBoundaryIsCountdownSwitchThenStart() {
        val twoDaysBefore = istMillis("2027-03-03 09:15")
        // 24 h before FP1
        assertEquals(istMillis("2027-03-04 09:15"), WeekendTimeline.nextBoundary(races, twoDaysBefore))

        // FP2 ends exactly now (13:30 + 30 min) → next boundary is SQ start
        val beforeSq = istMillis("2027-03-05 14:00")
        assertEquals(istMillis("2027-03-05 14:15"), WeekendTimeline.nextBoundary(races, beforeSq))

        assertNotNull(WeekendTimeline.nextBoundary(races, istMillis("2027-03-07 15:00")))
        assertNull(WeekendTimeline.nextBoundary(races, istMillis("2027-12-01 00:00")))
    }
}
