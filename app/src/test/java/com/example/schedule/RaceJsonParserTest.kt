package com.example.schedule

import com.example.schedule.data.RaceJsonParser
import com.example.schedule.data.ScheduleFormatException
import com.example.schedule.data.WeekendTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RaceJsonParserTest {

    @Test
    fun bundledScheduleIsValid() {
        // Unit tests run with the module folder (app/) as working directory
        for (year in listOf(2026, 2027)) {
            val season = RaceJsonParser.parse(File("src/main/assets/motogp_$year.json").readText())
            assertEquals(22, season.size)
            assertTrue(season.all { it.race.startsWith("$year-") })
        }
        // 2027 has Sprint Qualifying every round
        assertTrue(RaceJsonParser.parse(File("src/main/assets/motogp_2027.json").readText())
            .all { r -> r.sessions.any { it.sessionName == "sq" } })

        val races = RaceJsonParser.parse(File("src/main/assets/motogp_2026.json").readText())
        assertEquals("Buriram Grand Prix", races.first().name)
        assertEquals("Valencia Grand Prix", races.last().name)
    }

    @Test
    fun normalisesTimesAndFillsDefaults() {
        val json = """
            { "races": [ {
                "name": "Test GP", "location": "Somewhere, Nowhere", "round": 1,
                "race": "2028-03-03",
                "sessions": [
                  { "sessionName": "FP1", "sessionTime": "9:05am" },
                  { "sessionName": "race", "sessionTime": "01:30 PM" }
                ]
            } ] }
        """.trimIndent()

        val race = RaceJsonParser.parse(json).single()
        assertEquals("MotoGP", race.category)
        assertEquals("09:05 AM", race.sessions[0].sessionTime)
        assertEquals("fp1", race.sessions[0].sessionName)
        assertEquals(0, race.sessions[0].dayOffset)
        assertEquals(2, race.sessions[1].dayOffset)
        assertEquals("test-gp", race.slug)
    }

    @Test
    fun skipsNonMotoGpRaces() {
        val json = """
            [
              { "name": "A", "location": "X, Y", "category": "SBK", "race": "2028-03-03",
                "sessions": [ { "sessionName": "race", "sessionTime": "01:00 PM" } ] },
              { "name": "B", "location": "X, Y", "category": "MotoGP", "race": "2028-03-10",
                "sessions": [ { "sessionName": "race", "sessionTime": "01:00 PM" } ] }
            ]
        """.trimIndent()

        assertEquals(listOf("B"), RaceJsonParser.parse(json).map { it.name })
    }

    @Test(expected = ScheduleFormatException::class)
    fun rejectsImpossibleDate() {
        RaceJsonParser.parse(
            """{ "races": [ { "name": "A", "location": "X, Y", "race": "2028-02-30",
                "sessions": [ { "sessionName": "race", "sessionTime": "01:00 PM" } ] } ] }"""
        )
    }

    @Test(expected = ScheduleFormatException::class)
    fun rejectsBadTime() {
        RaceJsonParser.parse(
            """{ "races": [ { "name": "A", "location": "X, Y", "race": "2028-03-03",
                "sessions": [ { "sessionName": "race", "sessionTime": "25:00" } ] } ] }"""
        )
    }

    @Test(expected = ScheduleFormatException::class)
    fun rejectsNonJson() {
        RaceJsonParser.parse("hello")
    }

    @Test
    fun acceptsTbaTimeButLeavesItOutOfTheTimeline() {
        val race = RaceJsonParser.parse(
            """{ "races": [ { "name": "A", "location": "X, Y", "race": "2027-03-05",
                "sessions": [
                  { "sessionName": "fp2", "sessionTime": "01:30 PM", "dayOffset": 0 },
                  { "sessionName": "Sprint Qualifying", "sessionTime": "tba", "dayOffset": 0 }
                ] } ] }"""
        ).single()
        assertEquals("sq", race.sessions[1].sessionName)
        assertEquals(RaceJsonParser.TBA, race.sessions[1].sessionTime)
        assertEquals(1, WeekendTimeline.build(race).size)
    }
}
