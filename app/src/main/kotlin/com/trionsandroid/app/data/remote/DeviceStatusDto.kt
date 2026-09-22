package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Nightscout devicestatus document uploaded by Trio each loop cycle. Trio posts it through the
 * v1 API without a `date`, so the server sets `created_at` instead (a date-only query misses it).
 */
@Serializable
data class DeviceStatusDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    val date: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val openaps: OpenApsStatusDto? = null,
    val pump: PumpStatusDto? = null,
    // The battery of the phone Trio runs on (as opposed to the pump's own battery), for the Low
    // phone battery alarm and any future "uploader" display.
    val uploader: UploaderStatusDto? = null,
) {
    val stableId: String get() = identifier ?: legacyId ?: "${date}_$createdAt"
}

/**
 * devicestatus.pump. `reservoir` is absent when Omnipod reports 0xDEADBEEF ("50 U or more, exact
 * level unknown"), so "pump present, reservoir missing" means "50+". See Mappers.kt.
 */
@Serializable
data class PumpStatusDto(
    val reservoir: Double? = null,
)

/** devicestatus.uploader: the looping phone's own battery level (0-100), as it reports itself. */
@Serializable
data class UploaderStatusDto(
    val battery: Double? = null,
)

@Serializable
data class OpenApsStatusDto(
    // An object in what Trio uploads, an array in its local storage and other uploaders. Kept as
    // raw JSON and handled when mapping.
    val iob: JsonElement? = null,
    val suggested: DeterminationDto? = null,
    val enacted: DeterminationDto? = null,
)

@Serializable
data class DeterminationDto(
    @SerialName("IOB") val iob: Double? = null,
    @SerialName("COB") val cob: Double? = null,
    // The loop's plain-text explanation of its decision.
    val reason: String? = null,
    // Where glucose is expected to end up (mg/dL); Trio shows it next to its bubble.
    val eventualBG: Double? = null,
    val predBGs: PredictionsDto? = null,
    // ISO-8601; deliverAt is what Trio anchors the forecast to.
    val deliverAt: String? = null,
    val timestamp: String? = null,
)

/** Forecast curves in mg/dL, one value per 5 minutes. Any may be absent. */
@Serializable
data class PredictionsDto(
    @SerialName("IOB") val iob: List<Int>? = null,
    @SerialName("ZT") val zt: List<Int>? = null,
    @SerialName("COB") val cob: List<Int>? = null,
    @SerialName("UAM") val uam: List<Int>? = null,
)
