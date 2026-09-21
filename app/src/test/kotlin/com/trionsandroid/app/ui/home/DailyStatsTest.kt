package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.GlucoseTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DailyStatsTest {
    private val zone = ZoneId.of("UTC")
    private val midnight = LocalDate.now(zone).atStartOfDay(zone).toInstant()

    private fun readings(vararg values: Int) = values.mapIndexed { i, mgDl ->
        GlucoseReading("r$i", midnight.plusSeconds(60L * (i + 1)), mgDl, GlucoseTrend.Flat)
    }

    @Test
    fun noReadingsMeansNoData() {
        val stats = computeDailyStats(emptyList(), zone)
        assertFalse(stats.hasData)
        assertNull(stats.meanMgDl)
    }

    @Test
    fun readingsFromYesterdayAreIgnored() {
        val yesterday = GlucoseReading("old", midnight.minusSeconds(3600), 100, GlucoseTrend.Flat)
        assertFalse(computeDailyStats(listOf(yesterday), zone).hasData)
    }

    @Test
    fun rangesUseTheFixedBounds() {
        // very low, low, in range, in range, high, very high
        val stats = computeDailyStats(readings(50, 65, 100, 150, 190, 260), zone)
        assertTrue(stats.hasData)
        assertEquals(100.0 / 6, stats.veryLowPct, 0.01)
        assertEquals(100.0 / 6, stats.lowPct, 0.01)
        assertEquals(200.0 / 6, stats.inRangePct, 0.01)
        assertEquals(100.0 / 6, stats.highPct, 0.01)
        assertEquals(100.0 / 6, stats.veryHighPct, 0.01)
    }

    @Test
    fun boundariesBelongToTheInRangeBand() {
        val stats = computeDailyStats(readings(70, 180), zone)
        assertEquals(100.0, stats.inRangePct, 0.01)
    }

    @Test
    fun gmiFollowsTheFormula() {
        val stats = computeDailyStats(readings(100, 100), zone)
        assertEquals(3.31 + 0.02392 * 100, stats.gmiPercent!!, 0.0001)
    }
}
