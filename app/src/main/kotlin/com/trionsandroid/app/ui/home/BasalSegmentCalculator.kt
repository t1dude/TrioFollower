package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

data class BasalSegment(
    val startMillis: Long,
    val endMillis: Long,
    val rateUnitsPerHour: Double,
)

private const val TEMP_BASAL_EVENT_TYPE = "Temp Basal"
private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Builds the piecewise-constant basal rate over [viewportStartMillis, viewportEndMillis]: the
 * profile's repeating scheduled rate, overridden wherever a temp basal treatment is active.
 * Doesn't account for Profile Switch treatments — the given [profile] is treated as if it
 * applied for the whole window (see ProfileDocumentDto's doc comment for why).
 */
fun computeBasalSegments(
    viewportStartMillis: Long,
    viewportEndMillis: Long,
    profile: InsulinProfile?,
    treatments: List<Treatment>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<BasalSegment> {
    if (viewportEndMillis <= viewportStartMillis) return emptyList()

    val tempBasals = treatments
        .filter { it.eventType == TEMP_BASAL_EVENT_TYPE }
        .mapNotNull { treatment ->
            val rate = treatment.basalRateUnitsPerHour ?: return@mapNotNull null
            val durationMillis = treatment.durationMinutes?.let { (it * 60_000).toLong() } ?: return@mapNotNull null
            if (durationMillis <= 0) return@mapNotNull null
            val start = treatment.timestamp.toEpochMilli()
            val end = start + durationMillis
            if (end <= viewportStartMillis || start >= viewportEndMillis) return@mapNotNull null
            Triple(start, end, rate)
        }

    fun scheduledRateAt(millis: Long): Double {
        val schedule = profile?.basalSchedule
        if (schedule.isNullOrEmpty()) return 0.0
        val secondsOfDay = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime().toSecondOfDay()
        return schedule.lastOrNull { it.secondsFromMidnight <= secondsOfDay }?.rateUnitsPerHour
            ?: schedule.last().rateUnitsPerHour
    }

    val breakpoints = sortedSetOf(viewportStartMillis, viewportEndMillis)
    tempBasals.forEach { (start, end, _) ->
        breakpoints.add(start.coerceIn(viewportStartMillis, viewportEndMillis))
        breakpoints.add(end.coerceIn(viewportStartMillis, viewportEndMillis))
    }
    profile?.basalSchedule?.let { schedule ->
        var dayStartMillis = Instant.ofEpochMilli(viewportStartMillis)
            .atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        while (dayStartMillis < viewportEndMillis) {
            schedule.forEach { entry ->
                val millis = dayStartMillis + entry.secondsFromMidnight * 1000L
                if (millis in viewportStartMillis..viewportEndMillis) breakpoints.add(millis)
            }
            dayStartMillis += DAY_MILLIS
        }
    }

    val sortedPoints = breakpoints.toList()
    val rawSegments = mutableListOf<BasalSegment>()
    for (i in 0 until sortedPoints.size - 1) {
        val segStart = sortedPoints[i]
        val segEnd = sortedPoints[i + 1]
        if (segStart >= segEnd) continue
        val midpoint = segStart + (segEnd - segStart) / 2
        val overriding = tempBasals.firstOrNull { (start, end, _) -> midpoint in start until end }
        val rate = overriding?.third ?: scheduledRateAt(midpoint)
        rawSegments.add(BasalSegment(segStart, segEnd, rate))
    }

    // Merge adjacent segments with (near-)identical rates so tiny floating-point-driven steps
    // don't show up as visual noise.
    val merged = mutableListOf<BasalSegment>()
    rawSegments.forEach { segment ->
        val last = merged.lastOrNull()
        if (last != null && abs(last.rateUnitsPerHour - segment.rateUnitsPerHour) < 0.001) {
            merged[merged.lastIndex] = last.copy(endMillis = segment.endMillis)
        } else {
            merged.add(segment)
        }
    }
    return merged
}
