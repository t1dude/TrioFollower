package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The v3 API's {status, result} envelope, with each result item left as raw JSON rather
 * than eagerly decoded to a specific DTO. Different Loop/AndroidAPS/Trio uploaders write
 * slightly different shapes into the same Nightscout collections, so decoding the whole
 * array in one shot means a single malformed record throws away every other record in the
 * batch. Callers decode each element individually and can skip-and-log the odd one out.
 */
@Serializable
data class NightscoutV3Envelope(
    val status: Int,
    val result: List<JsonElement> = emptyList(),
)
