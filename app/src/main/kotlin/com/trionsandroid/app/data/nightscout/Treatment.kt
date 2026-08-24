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
)
