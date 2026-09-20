package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.Flow

interface NightscoutRepository {
    fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>>
    fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>>

    /** IOB and COB as computed and uploaded by Trio. */
    fun observeDeviceStatus(sinceMillis: Long): Flow<List<DeviceStatusPoint>>

    /** The latest insulin profile (basal schedule and DIA), or null before the first fetch. Not cached in Room. */
    fun observeInsulinProfile(): Flow<InsulinProfile?>

    /** The loop's latest glucose forecast, or null. */
    fun observeLatestForecast(): Flow<Forecast?>

    /**
     * The reasoning for the reading at [readingTimestamp]: the first determination received from
     * shortly before it until just under the next reading. Null if none is cached.
     */
    suspend fun getReasoningForReading(readingTimestamp: java.time.Instant): Reasoning?

    /** Fetches recent data from Nightscout into the local cache. */
    suspend fun refresh(lookbackHours: Int = 24): Result<Unit>
}
