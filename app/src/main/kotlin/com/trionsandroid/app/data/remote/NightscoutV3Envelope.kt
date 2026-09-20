package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The v3 {status, result} envelope. Items stay raw JSON so one malformed record can be skipped and logged. */
@Serializable
data class NightscoutV3Envelope(
    val status: Int,
    val result: List<JsonElement> = emptyList(),
)
