package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.Flow

interface NightscoutRepository {
    fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>>
    fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>>

    /** IOB/COB as Trio's own loop engine computed and uploaded them, not derived locally. */
    fun observeDeviceStatus(sinceMillis: Long): Flow<List<DeviceStatusPoint>>

    /**
     * The most recently fetched insulin profile (basal schedule + DIA), or null before the first
     * successful fetch. Not cached to Room — it's small and cheap to re-fetch, and doesn't need
     * offline availability the way glucose/treatment history does.
     */
    fun observeInsulinProfile(): Flow<InsulinProfile?>

    /** The loop's most recent glucose forecast, or null if none has been cached. */
    fun observeLatestForecast(): Flow<Forecast?>

    /**
     * The algorithm reasoning that goes with the glucose reading at [readingTimestamp]: the first
     * determination Nightscout received from shortly before that reading (clock slack) up to just
     * under the next reading's arrival, since the loop runs right after each new reading. Null if
     * none was cached (e.g. older than the first fetch after reasoning support was added).
     */
    suspend fun getReasoningForReading(readingTimestamp: java.time.Instant): Reasoning?

    /** Fetches recent data from Nightscout and upserts it into the local cache. */
    suspend fun refresh(lookbackHours: Int = 24): Result<Unit>
}
