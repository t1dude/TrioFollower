package com.trionsandroid.app.data.nightscout

import java.time.Instant

data class Treatment(
    val id: String,
    val timestamp: Instant,
    val eventType: String,
    val insulinUnits: Double?,
    val carbsGrams: Double?,
    val durationMinutes: Double?,
    val basalRateUnitsPerHour: Double?,
    /** An override or temp target's name (e.g. "Boost"). */
    val notes: String?,
    /** A temp target's target in mg/dL. Always null for overrides. */
    val targetMgDl: Double?,
)
