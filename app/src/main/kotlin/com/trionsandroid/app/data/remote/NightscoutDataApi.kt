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
     * Same query on `created_at`: some treatments never get an indexed `date`, so getTreatments()
     * can miss them. Results overlap and are merged by identifier.
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

    @GET("api/v3/devicestatus")
    suspend fun getDeviceStatus(
        @Header("Authorization") bearerToken: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    /** devicestatus needs the same date/created_at dual query as treatments. */
    @GET("api/v3/devicestatus")
    suspend fun getDeviceStatusByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    /** Latest "Site Change" or "Sensor Start". Rare, so it looks back much further than 24h with limit=1. */
    @GET("api/v3/treatments")
    suspend fun getLatestLifecycleEvent(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("date\$gte") sinceMillis: Long,
        // Upper bound so a bogus far-future entry (e.g. year 2162) can't take the single result slot.
        @Query("date\$lte") untilMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1,
    ): NightscoutV3Envelope

    /** created_at variant of getLatestLifecycleEvent. */
    @GET("api/v3/treatments")
    suspend fun getLatestLifecycleEventByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("created_at\$lte") untilMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 1,
    ): NightscoutV3Envelope

    /** Overrides ("Exercise") and temp targets ("Temporary Target"): a longer lookback and a real limit. */
    @GET("api/v3/treatments")
    suspend fun getAdjustments(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 200,
    ): NightscoutV3Envelope

    /** created_at variant of getAdjustments. */
    @GET("api/v3/treatments")
    suspend fun getAdjustmentsByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 200,
    ): NightscoutV3Envelope
}
