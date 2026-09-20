package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.nightscout.isTempBasalEventType
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

data class BasalSegment(
    val startMillis: Long,
    val endMillis: Long,
    val rateUnitsPerHour: Double,
)

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

// Absorbs upload jitter between consecutive temp basals without bridging a real return to the schedule.
private const val GRACE_PERIOD_MILLIS = 10 * 60 * 1000L

/**
 * Basal rate over the viewport: the scheduled rate, overridden wherever a temp basal is active.
 * Profile switches are not handled; [profile] is assumed to apply to the whole window.
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
        .filter { isTempBasalEventType(it.eventType) }
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
        // As in Trio: the most recently started temp basal wins, so a stale superseded entry can't
        // cause false drops to the scheduled rate. A grace period past a temp's end absorbs late
        // uploads of its successor; a longer gap counts as a return to the schedule.
        val overriding = tempBasals
            .filter { (start, _, _) -> start <= midpoint }
            .maxByOrNull { (start, _, _) -> start }
            ?.takeIf { (_, end, _) -> midpoint < end + GRACE_PERIOD_MILLIS }
        val rate = overriding?.third ?: scheduledRateAt(midpoint)
        rawSegments.add(BasalSegment(segStart, segEnd, rate))
    }

    // Merge neighbouring segments with near-identical rates.
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

private const val DOMAIN_MAX_LOOKBACK_HOURS = 24L

/**
 * Y-axis ceiling of the basal strip, as in Trio's basalDomainMax: the max of recent temp rates and
 * scheduled rates. Uses a fixed recent lookback, not the viewport, so the scale doesn't jump when panning.
 */
fun basalDomainMaxRate(
    nowMillis: Long,
    profile: InsulinProfile?,
    treatments: List<Treatment>,
): Double {
    val domainStartMillis = nowMillis - DOMAIN_MAX_LOOKBACK_HOURS * 60 * 60 * 1000L
    val recentTempMax = treatments
        .asSequence()
        .filter { isTempBasalEventType(it.eventType) && it.timestamp.toEpochMilli() >= domainStartMillis }
        .mapNotNull { it.basalRateUnitsPerHour }
        .maxOrNull() ?: 0.0
    val profileMax = profile?.basalSchedule?.maxOfOrNull { it.rateUnitsPerHour } ?: 0.0
    return maxOf(recentTempMax, profileMax, 0.1)
}
