package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntryDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    // Nightscout sometimes reports `date` with a fractional-millisecond component
    // (e.g. 1787474479807.234) depending on the uploader, so this can't be a Long.
    val date: Double,
    val sgv: Int? = null,
    val direction: String? = null,
    val type: String? = null,
    val device: String? = null,
) {
    /** API v3 falls back `identifier` to the internal `_id` when absent — mirror that here. */
    val stableId: String get() = identifier ?: legacyId ?: date.toString()
}
