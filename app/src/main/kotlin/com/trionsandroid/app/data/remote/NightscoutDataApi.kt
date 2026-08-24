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

    /**
     * Treatments have historically used `created_at` (not `date`) as their canonical timestamp
     * in Nightscout, and a document written via that older path may never get an indexed `date`
     * field at all — meaning getTreatments() above can silently miss it even though it's fully
     * visible on the classic dashboard. `created_at` is filterable, distinctly from `date`, in
     * the v3 query engine, so this is a second query on that field. Results from both need
     * merging by identifier since they can overlap.
     */
    @GET("api/v3/treatments")
    suspend fun getTreatmentsByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    @GET("api/v3/profile")
    suspend fun getProfile(
        @Header("Authorization") bearerToken: String,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1,
    ): NightscoutV3Envelope
}
