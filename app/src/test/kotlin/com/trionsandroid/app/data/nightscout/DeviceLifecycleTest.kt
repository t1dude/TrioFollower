package com.trionsandroid.app.data.nightscout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DeviceLifecycleTest {
    private val now = Instant.parse("2026-09-21T10:00:00Z")

    private fun event(eventType: String, daysAgo: Long) = Treatment(
        id = "$eventType-$daysAgo",
        timestamp = now.minus(Duration.ofDays(daysAgo)),
        eventType = eventType,
        insulinUnits = null,
        carbsGrams = null,
        durationMinutes = null,
        basalRateUnitsPerHour = null,
        notes = null,
        targetMgDl = null,
    )

    @Test
    fun sensorRemainingCountsDownFromTenDays() {
        val remaining = sensorTimeRemaining(listOf(event(SENSOR_START_EVENT_TYPE, daysAgo = 4)), now)
        assertEquals(Duration.ofDays(6), remaining)
    }

    @Test
    fun siteRemainingCountsDownFromThreeDays() {
        val remaining = siteTimeRemaining(listOf(event(SITE_CHANGE_EVENT_TYPE, daysAgo = 1)), now)
        assertEquals(Duration.ofDays(2), remaining)
    }

    @Test
    fun overdueChangeIsNegative() {
        val remaining = siteTimeRemaining(listOf(event(SITE_CHANGE_EVENT_TYPE, daysAgo = 5)), now)
        assertEquals(true, remaining!!.isNegative)
    }

    @Test
    fun onlyTheLatestEventCounts() {
        val remaining = sensorTimeRemaining(
            listOf(event(SENSOR_START_EVENT_TYPE, daysAgo = 9), event(SENSOR_START_EVENT_TYPE, daysAgo = 1)),
            now,
        )
        assertEquals(Duration.ofDays(9), remaining)
    }

    @Test
    fun noEventMeansUnknown() {
        assertNull(sensorTimeRemaining(emptyList(), now))
        assertNull(siteTimeRemaining(listOf(event(SENSOR_START_EVENT_TYPE, daysAgo = 1)), now))
    }

    @Test
    fun formatsDaysHoursMinutesAndOverdue() {
        assertEquals("2d 4h", formatTimeRemaining(Duration.ofHours(52)))
        assertEquals("15h", formatTimeRemaining(Duration.ofHours(15)))
        assertEquals("6h 30m", formatTimeRemaining(Duration.ofMinutes(6 * 60 + 30)))
        assertEquals("45m", formatTimeRemaining(Duration.ofMinutes(45)))
        assertEquals("Replace", formatTimeRemaining(Duration.ofMinutes(-5)))
        assertEquals("Replace", formatTimeRemaining(Duration.ZERO))
    }
}
