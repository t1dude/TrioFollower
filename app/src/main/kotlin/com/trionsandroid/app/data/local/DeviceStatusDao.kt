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

    @Query("DELETE FROM device_status WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
