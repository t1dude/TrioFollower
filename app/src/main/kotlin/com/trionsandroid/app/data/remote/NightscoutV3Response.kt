package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class NightscoutV3Response<T>(
    val status: Int,
    val result: List<T> = emptyList(),
)
