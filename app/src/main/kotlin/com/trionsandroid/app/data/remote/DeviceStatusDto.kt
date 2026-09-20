package com.trionsandroid.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Nightscout devicestatus document, as uploaded by Trio's loop engine every cycle.
 *
 * Confirmed against Trio's own upload code (NightscoutAPI.swift's uploadDeviceStatus): it POSTs
 * the NightscoutStatus struct directly to the legacy v1 devicestatus endpoint with no date or
 * created_at field of its own — Nightscout's server assigns created_at on receipt for a v1
 * insert like this, not date, exactly like the treatments case. (An earlier version of this
 * comment claimed devicestatus was reliably date-keyed; that was a guess and it was wrong —
 * confirmed empirically via an empty date$gte result despite the data existing.)
 */
@Serializable
data class DeviceStatusDto(
    @SerialName("_id") val legacyId: String? = null,
    val identifier: String? = null,
    val date: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val openaps: OpenApsStatusDto? = null,
    val pump: PumpStatusDto? = null,
) {
    val stableId: String get() = identifier ?: legacyId ?: "${date}_$createdAt"
}

/**
 * Nightscout devicestatus.pump, confirmed against Trio's NSPumpStatus (NightscoutStatus.swift).
 * `reservoir` is nullable for a real reason, not just optionality: Omnipod reports 0xDEADBEEF
 * locally for "at least 50U, exact level unknown" (PumpView.swift), and NightscoutManager.swift's
 * upload code (`reservoir: reservoir != 0xDEAD_BEEF ? reservoir : nil`) turns that into an
 * *absent* field on the wire — so "pump present, reservoir missing" is itself the "50+" signal,
 * not just missing data. See the mapping in Mappers.kt.
 */
@Serializable
data class PumpStatusDto(
    val reservoir: Double? = null,
)

@Serializable
data class OpenApsStatusDto(
    // Trio's own local (pre-upload) storage keeps this as an array (github.com/nightscout/Trio,
    // NightscoutManager.swift: "storage.retrieveAsync(OpenAPS.Monitor.iob, as: [IOBEntry].self)"),
    // while the NightscoutStatus struct actually uploaded types it as a single object — and other
    // uploaders may differ again. Left as raw JSON and disambiguated when mapping to the domain
    // model so either shape works.
    val iob: JsonElement? = null,
    val suggested: DeterminationDto? = null,
    val enacted: DeterminationDto? = null,
)

@Serializable
data class DeterminationDto(
    @SerialName("IOB") val iob: Double? = null,
    @SerialName("COB") val cob: Double? = null,
    // The loop's plain-text explanation of the decision (Determination.reason in Trio).
    val reason: String? = null,
    val predBGs: PredictionsDto? = null,
    // ISO-8601 timestamps; deliverAt is what Trio itself anchors the forecast to.
    val deliverAt: String? = null,
    val timestamp: String? = null,
)

/** oref forecast curves (Trio's `Predictions`): mg/dL, one value per 5 minutes. Any may be absent. */
@Serializable
data class PredictionsDto(
    @SerialName("IOB") val iob: List<Int>? = null,
    @SerialName("ZT") val zt: List<Int>? = null,
    @SerialName("COB") val cob: List<Int>? = null,
    @SerialName("UAM") val uam: List<Int>? = null,
)
