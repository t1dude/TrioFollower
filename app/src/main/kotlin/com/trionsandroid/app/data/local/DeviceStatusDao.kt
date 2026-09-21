package com.trionsandroid.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStatusDao {
    @Upsert
    suspend fun upsertAll(entries: List<DeviceStatusEntity>)

    @Query("SELECT * FROM device_status WHERE dateMillis >= :sinceMillis ORDER BY dateMillis ASC")
    fun observeSince(sinceMillis: Long): Flow<List<DeviceStatusEntity>>

    /** Same rows without the large `reason` and forecast columns, for the IOB/COB/HUD lists. */
    @Query(
        "SELECT id, dateMillis, iobUnits, cobGrams, reservoirUnits, eventualBgMgDl FROM device_status " +
            "WHERE dateMillis >= :sinceMillis ORDER BY dateMillis ASC",
    )
    fun observeSummariesSince(sinceMillis: Long): Flow<List<DeviceStatusSummary>>

    /** The most recent status that carried forecast curves. */
    @Query("SELECT * FROM device_status WHERE forecastStartMillis IS NOT NULL ORDER BY dateMillis DESC LIMIT 1")
    fun observeLatestForecast(): Flow<DeviceStatusEntity?>

    /** Earliest determination with reasoning text in [fromMillis, toMillis). */
    @Query(
        "SELECT * FROM device_status WHERE reason IS NOT NULL AND dateMillis >= :fromMillis " +
            "AND dateMillis < :toMillis ORDER BY dateMillis ASC LIMIT 1",
    )
    suspend fun firstWithReasonBetween(fromMillis: Long, toMillis: Long): DeviceStatusEntity?

    @Query("DELETE FROM device_status WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
