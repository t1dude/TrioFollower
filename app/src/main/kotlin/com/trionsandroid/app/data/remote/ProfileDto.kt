package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Nightscout profile document. Named profiles are under `store` with `defaultProfile` naming the
 * active one; older documents have the fields on the root, which is tolerated. Profile Switch
 * treatments are ignored: the default profile is used for the whole window.
 */
@Serializable
data class ProfileDocumentDto(
    val defaultProfile: String? = null,
    val store: Map<String, ProfileStoreEntryDto>? = null,
    val dia: JsonElement? = null,
    val basal: List<BasalScheduleEntryDto>? = null,
)

@Serializable
data class ProfileStoreEntryDto(
    val dia: JsonElement? = null,
    val basal: List<BasalScheduleEntryDto>? = null,
)

@Serializable
data class BasalScheduleEntryDto(
    val time: String? = null,
    val value: Double? = null,
    val timeAsSeconds: Int? = null,
)
