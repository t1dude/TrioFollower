package com.trionsandroid.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NightscoutDataApi {
    @GET("api/v3/entries")
    suspend fun getEntries(
        @Header("Authorization") bearerToken: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    @GET("api/v3/treatments")
    suspend fun getTreatments(
        @Header("Authorization") bearerToken: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope
}
