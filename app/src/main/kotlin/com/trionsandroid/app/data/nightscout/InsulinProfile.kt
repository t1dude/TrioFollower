package com.trionsandroid.app.data.nightscout

data class BasalScheduleEntry(
    val secondsFromMidnight: Int,
    val rateUnitsPerHour: Double,
)

data class InsulinProfile(
    val diaHours: Double,
    /** Sorted by secondsFromMidnight, ascending. */
    val basalSchedule: List<BasalScheduleEntry>,
)
