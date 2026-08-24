package com.trionsandroid.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {
    @Upsert
    suspend fun upsertAll(treatments: List<TreatmentEntity>)

    @Query("SELECT * FROM treatments WHERE dateMillis >= :sinceMillis ORDER BY dateMillis ASC")
    fun observeSince(sinceMillis: Long): Flow<List<TreatmentEntity>>

    @Query("DELETE FROM treatments WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
