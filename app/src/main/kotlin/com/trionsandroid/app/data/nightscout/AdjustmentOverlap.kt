package com.trionsandroid.app.data.nightscout

import java.time.Duration

/**
 * Trio has only one override and one temp target active at a time, so a newer one always ends the
 * previous. Nightscout can still list the earlier one with its long placeholder duration, which
 * would draw the two on top of each other. Cuts each such entry short at the next one's start.
 */
fun List<Treatment>.withOverlappingAdjustmentsClipped(): List<Treatment> {
    // Exact duplicates (same type, start and name) can't be told apart by time, so keep one,
    // preferring the shortest duration (a real end over a stale placeholder).
    val duplicateIds = HashSet<String>()
    filter { it.eventType in ADJUSTMENT_EVENT_TYPES }
        .groupBy { Triple(it.eventType, it.timestamp, it.notes) }
        .values
        .forEach { group ->
            if (group.size > 1) {
                val keep = group.minBy { it.durationMinutes ?: Double.MAX_VALUE }
                group.filter { it !== keep }.forEach { duplicateIds += it.id }
            }
        }
    if (duplicateIds.isNotEmpty()) return filter { it.id !in duplicateIds }.withOverlappingAdjustmentsClipped()

    val clippedMinutes = HashMap<String, Double>()
    for (type in ADJUSTMENT_EVENT_TYPES) {
        filter { it.eventType == type }
            .sortedBy { it.timestamp }
            .zipWithNext { current, next ->
                if (next.timestamp == current.timestamp) return@zipWithNext
                val end = current.timestamp.plusSeconds(((current.durationMinutes ?: 0.0) * 60).toLong())
                if (end.isAfter(next.timestamp)) {
                    clippedMinutes[current.id] = Duration.between(current.timestamp, next.timestamp).seconds / 60.0
                }
            }
    }
    if (clippedMinutes.isEmpty()) return this
    return map { treatment -> clippedMinutes[treatment.id]?.let { treatment.copy(durationMinutes = it) } ?: treatment }
}
