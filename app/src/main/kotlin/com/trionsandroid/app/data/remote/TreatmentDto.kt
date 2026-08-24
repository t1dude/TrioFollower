package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TreatmentDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    // See EntryDto.date — Nightscout can report a fractional-millisecond value here too.
    val date: Double? = null,
    val eventType: String? = null,
    val insulin: Double? = null,
    val carbs: Double? = null,
    val duration: Double? = null,
    /** Temp basal absolute rate in U/hr. */
    val absolute: Double? = null,
) {
    val stableId: String get() = identifier ?: legacyId ?: "${date}_$eventType"
}
