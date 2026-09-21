package com.trionsandroid.app.data.nightscout

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AdjustmentOverlapTest {
    private val start = Instant.parse("2026-09-21T10:00:00Z")

    private fun override(id: String, minutesAfterStart: Long, duration: Double, notes: String = "Boost") = Treatment(
        id = id,
        timestamp = start.plusSeconds(minutesAfterStart * 60),
        eventType = OVERRIDE_EVENT_TYPE,
        insulinUnits = null,
        carbsGrams = null,
        durationMinutes = duration,
        basalRateUnitsPerHour = null,
        notes = notes,
        targetMgDl = null,
    )

    @Test
    fun earlierOverrideEndsWhenTheNextStarts() {
        val result = listOf(override("a", 0, 43_200.0), override("b", 60, 30.0)).withOverlappingAdjustmentsClipped()
        assertEquals(60.0, result.first { it.id == "a" }.durationMinutes!!, 0.001)
        assertEquals(30.0, result.first { it.id == "b" }.durationMinutes!!, 0.001)
    }

    @Test
    fun overrideThatAlreadyEndedIsUnchanged() {
        val result = listOf(override("a", 0, 30.0), override("b", 60, 30.0)).withOverlappingAdjustmentsClipped()
        assertEquals(30.0, result.first { it.id == "a" }.durationMinutes!!, 0.001)
    }

    @Test
    fun exactDuplicatesKeepTheShortestDuration() {
        val result = listOf(override("long", 0, 43_200.0), override("short", 0, 90.0)).withOverlappingAdjustmentsClipped()
        assertEquals(listOf("short"), result.map { it.id })
    }

    @Test
    fun otherTreatmentsAreLeftAlone() {
        val bolus = Treatment("x", start, "Correction Bolus", 1.0, null, null, null, null, null)
        assertEquals(listOf(bolus), listOf(bolus).withOverlappingAdjustmentsClipped())
    }
}
