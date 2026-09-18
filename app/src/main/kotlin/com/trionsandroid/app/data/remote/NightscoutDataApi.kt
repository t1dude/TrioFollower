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

    @GET("api/v3/devicestatus")
    suspend fun getDeviceStatus(
        @Header("Authorization") bearerToken: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    /** See DeviceStatusDto's doc comment — devicestatus needs the same date/created_at dual
     * query as treatments, for the same underlying reason. */
    @GET("api/v3/devicestatus")
    suspend fun getDeviceStatusByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 1000,
    ): NightscoutV3Envelope

    /**
     * Just the single most recent occurrence of a pump/CGM lifecycle event (e.g. "Site Change",
     * "Sensor Start"), for the HUD's time-remaining pills. These are rare (days apart) so the
     * regular getTreatments() 24h window usually won't catch them — this queries much further
     * back but only needs limit=1.
     */
    @GET("api/v3/treatments")
    suspend fun getLatestLifecycleEvent(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 1,
    ): NightscoutV3Envelope

    /** See getLatestLifecycleEvent — same created_at fallback reason as treatments/devicestatus. */
    @GET("api/v3/treatments")
    suspend fun getLatestLifecycleEventByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 1,
    ): NightscoutV3Envelope

    /**
     * Overrides ("Exercise") and temp targets ("Temporary Target") — unlike Site Change/Sensor
     * Start, more than one of these can matter at a time (History wants the whole list, not just
     * the latest), so this queries much further back than the regular 24h treatments window but
     * with a real limit rather than 1.
     */
    @GET("api/v3/treatments")
    suspend fun getAdjustments(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("date\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "date",
        @Query("limit") limit: Int = 200,
    ): NightscoutV3Envelope

    /** See getAdjustments — same created_at fallback reason as treatments/devicestatus. */
    @GET("api/v3/treatments")
    suspend fun getAdjustmentsByCreatedAt(
        @Header("Authorization") bearerToken: String,
        @Query("eventType\$eq") eventType: String,
        @Query("created_at\$gte") sinceMillis: Long,
        @Query("sort\$desc") sort: String = "created_at",
        @Query("limit") limit: Int = 200,
    ): NightscoutV3Envelope
}
