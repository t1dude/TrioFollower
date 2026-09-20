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

    @Query("DELETE FROM treatments WHERE dateMillis > :afterMillis")
    suspend fun deleteNewerThan(afterMillis: Long)

    @Query("DELETE FROM treatments WHERE dateMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    /**
     * Deletes cached rows of these eventTypes whose id isn't in [keepIds]. Trio replaces an ended
     * override's entry under a new id, so the old one would otherwise stay as a duplicate.
     */
    @Query("DELETE FROM treatments WHERE eventType IN (:eventTypes) AND dateMillis >= :sinceMillis AND id NOT IN (:keepIds)")
    suspend fun deleteStaleAdjustments(eventTypes: List<String>, sinceMillis: Long, keepIds: List<String>)
}
