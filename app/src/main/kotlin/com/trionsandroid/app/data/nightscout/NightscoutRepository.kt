package com.trionsandroid.app.data.nightscout

import kotlinx.coroutines.flow.Flow

interface NightscoutRepository {
    fun observeGlucoseEntries(sinceMillis: Long): Flow<List<GlucoseReading>>
    fun observeTreatments(sinceMillis: Long): Flow<List<Treatment>>

    /** Fetches recent data from Nightscout and upserts it into the local cache. */
    suspend fun refresh(lookbackHours: Int = 24): Result<Unit>
}
