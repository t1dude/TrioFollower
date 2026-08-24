package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.Flow

interface NightscoutRepository {
    fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>>
    fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>>

    /**
     * The most recently fetched insulin profile (basal schedule + DIA), or null before the first
     * successful fetch. Not cached to Room — it's small and cheap to re-fetch, and doesn't need
     * offline availability the way glucose/treatment history does.
     */
    fun observeInsulinProfile(): Flow<InsulinProfile?>

    /** Fetches recent data from Nightscout and upserts it into the local cache. */
    suspend fun refresh(lookbackHours: Int = 24): Result<Unit>
}
