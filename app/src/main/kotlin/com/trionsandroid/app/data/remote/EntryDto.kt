package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EntryDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    val date: Long,
    val sgv: Int? = null,
    val direction: String? = null,
    val type: String? = null,
    val device: String? = null,
) {
    /** API v3 falls back `identifier` to the internal `_id` when absent — mirror that here. */
    val stableId: String get() = identifier ?: legacyId ?: date.toString()
}
