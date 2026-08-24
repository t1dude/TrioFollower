package com.trionsandroid.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseEntryDao {
    @Upsert
    suspend fun upsertAll(entries: List<GlucoseEntryEntity>)

    @Query("SELECT * FROM glucose_entries WHERE dateMillis >= :sinceMillis ORDER BY dateMillis ASC")
    fun observeSince(sinceMillis: Long): Flow<List<GlucoseEntryEntity>>

    @Query("DELETE FROM glucose_entries WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
