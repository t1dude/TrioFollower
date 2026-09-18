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
    /** An override/temp target's name/reason (e.g. "Boost"). Null for treatment types that don't
     *  carry one. */
    val notes: String?,
    /** A temp target's target glucose value in mg/dL. Always null for overrides — Nightscout
     *  never has this for them; see TreatmentDto's doc comment. */
    val targetMgDl: Double?,
)
