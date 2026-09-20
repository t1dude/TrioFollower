package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.GlucoseReading
import java.time.LocalDate
import java.time.ZoneId

// Trio's fixed bounds, not the alarm thresholds, so numbers match its Stats screen.
private const val VERY_LOW_BELOW = 54
private const val IN_RANGE_FROM = 70
private const val IN_RANGE_TO = 180
private const val VERY_HIGH_ABOVE = 250

/** Today's distribution (percent, since local midnight) and mean glucose. */
data class DailyStats(
    val hasData: Boolean,
    val veryLowPct: Double,
    val lowPct: Double,
    val inRangePct: Double,
    val highPct: Double,
    val veryHighPct: Double,
    val meanMgDl: Double?,
) {
    val gmiPercent: Double? get() = meanMgDl?.let { 3.31 + 0.02392 * it }
}

fun computeDailyStats(readings: List<GlucoseReading>, zone: ZoneId = ZoneId.systemDefault()): DailyStats {
    val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
    val values = readings.filter { it.timestamp >= startOfDay }.map { it.mgDl }
    if (values.isEmpty()) return DailyStats(false, 0.0, 0.0, 0.0, 0.0, 0.0, null)
    val total = values.size.toDouble()
    fun pct(count: Int) = count / total * 100
    return DailyStats(
        hasData = true,
        veryLowPct = pct(values.count { it < VERY_LOW_BELOW }),
        lowPct = pct(values.count { it in VERY_LOW_BELOW until IN_RANGE_FROM }),
        inRangePct = pct(values.count { it in IN_RANGE_FROM..IN_RANGE_TO }),
        highPct = pct(values.count { it in (IN_RANGE_TO + 1)..VERY_HIGH_ABOVE }),
        veryHighPct = pct(values.count { it > VERY_HIGH_ABOVE }),
        meanMgDl = values.average(),
    )
}
