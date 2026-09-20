package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntryDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    // Can be fractional milliseconds (e.g. 1787474479807.234), so not a Long.
    val date: Double,
    val sgv: Int? = null,
    val direction: String? = null,
    val type: String? = null,
    val device: String? = null,
) {
    /** API v3 falls back to `_id` when `identifier` is absent. */
    val stableId: String get() = identifier ?: legacyId ?: date.toString()
}
