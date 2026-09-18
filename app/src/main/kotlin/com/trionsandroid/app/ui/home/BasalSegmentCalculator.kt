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

// Generous enough to absorb normal loop-cycle upload jitter (Trio typically re-announces an
// active temp basal every ~5 minutes) between two consecutive temp basals, without being so
// wide it would bridge a genuine, longer return to scheduled/open-loop operation.
private const val GRACE_PERIOD_MILLIS = 10 * 60 * 1000L

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
        // Mirrors Trio's own resolution (BasalChart.swift calculateTempBasals): the
        // most-recently-*started* temp basal governs, superseding any earlier one immediately —
        // even across a small gap between two re-announcements of the same temp — rather than
        // picking whichever overlapping entry happens to come first in the treatments list.
        // (Nightscout/OpenAPS re-uploads an active temp basal's "Temp Basal" treatment
        // periodically; the old firstOrNull-on-[start,end) logic could pick a stale superseded
        // entry, producing phantom reversions to the scheduled rate — i.e. spikes — during what
        // was actually a steady active temp.)
        //
        // A GRACE_PERIOD_MILLIS tolerance past that temp's own nominal end absorbs ordinary
        // announcement jitter between one temp basal ending and its successor's upload landing
        // a couple of minutes late — without it, that brief gap falls through to the scheduled
        // rate and shows up as its own short spike, even between two announcements of the exact
        // same (e.g. zero) rate. Only a gap wider than the grace period is treated as a genuine
        // return to scheduled/open-loop operation.
        val overriding = tempBasals
            .filter { (start, _, _) -> start <= midpoint }
            .maxByOrNull { (start, _, _) -> start }
            ?.takeIf { (_, end, _) -> midpoint < end + GRACE_PERIOD_MILLIS }
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

private const val DOMAIN_MAX_LOOKBACK_HOURS = 24L

/**
 * The basal strip's y-axis ceiling: matches Trio's `basalDomainMax` (BasalChart.swift) — the max
 * of recent temp basal rates and the profile's own scheduled rates, each a floor of 0.1. Crucially
 * this is computed over a fixed recent lookback ending "now", independent of whatever time range
 * the chart happens to be zoomed/panned to. Scaling to the current *viewport's* max instead (as an
 * earlier version of this chart did) made the strip's scale jump around while zooming/panning, and
 * exaggerated minor rate variations into visually oversized bars whenever the visible window
 * didn't happen to include the day's actual peak rate.
 */
fun basalDomainMaxRate(
    nowMillis: Long,
    profile: InsulinProfile?,
    treatments: List<Treatment>,
): Double {
    val domainStartMillis = nowMillis - DOMAIN_MAX_LOOKBACK_HOURS * 60 * 60 * 1000L
    val recentTempMax = treatments
        .asSequence()
        .filter { it.eventType == TEMP_BASAL_EVENT_TYPE && it.timestamp.toEpochMilli() >= domainStartMillis }
        .mapNotNull { it.basalRateUnitsPerHour }
        .maxOrNull() ?: 0.0
    val profileMax = profile?.basalSchedule?.maxOfOrNull { it.rateUnitsPerHour } ?: 0.0
    return maxOf(recentTempMax, profileMax, 0.1)
}
