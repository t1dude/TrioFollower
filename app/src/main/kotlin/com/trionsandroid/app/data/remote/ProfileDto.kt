package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Nightscout's profile document. Modern documents wrap named profiles under `store`, keyed by
 * name, with `defaultProfile` naming which one is active — but older documents (predating
 * multi-profile support) put the fields directly on the root, which this also tolerates as a
 * fallback (mirroring Nightscout's own profilefunctions.js migration shim).
 *
 * Deliberately not handling Profile Switch treatments that change which named profile is active
 * over time — this always uses whichever profile `defaultProfile` currently names, applied as if
 * it covered the whole visible window.
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
