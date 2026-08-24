package com.trionsandroid.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

interface NightscoutAuthApi {
    /** Exchanges a Nightscout access token for a short-lived JWT used to call the v3 API. */
    @GET("api/v2/authorization/request/{accessToken}")
    suspend fun requestAuthorization(@Path("accessToken") accessToken: String): AuthorizationResponse
}

@Serializable
data class AuthorizationResponse(
    val token: String,
    val sub: String? = null,
    val rights: JsonObject? = null,
)
