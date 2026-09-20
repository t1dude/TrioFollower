package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TreatmentDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    // May be fractional milliseconds (see EntryDto.date).
    val date: Double? = null,
    // Fallback timestamp when `date` is absent.
    @SerialName("created_at") val createdAt: String? = null,
    val eventType: String? = null,
    val insulin: Double? = null,
    val carbs: Double? = null,
    val duration: Double? = null,
    /** Temp basal absolute rate in U/hr. */
    val absolute: Double? = null,
    /** An override or temp target's name; Trio has no separate name field. */
    val notes: String? = null,
    /** A temp target's target in mg/dL (Trio uploads top == bottom). Overrides have none. */
    val targetTop: Double? = null,
) {
    val stableId: String get() = identifier ?: legacyId ?: "${date}_$eventType"
}
