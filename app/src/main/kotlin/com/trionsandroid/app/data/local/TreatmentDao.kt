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

    /**
     * Reconciles a set of eventTypes against what Nightscout just returned for them, deleting any
     * previously-cached row in that window whose id isn't in [keepIds]. Needed for
     * overrides/temp targets specifically: Trio doesn't edit an override's Nightscout entry when
     * it ends — it deletes the original placeholder-duration entry (matched by created_at) and
     * uploads a brand new one, under a different id, with the real final duration (see
     * OverrideStorage.swift's uploadOverrideRuns). A plain upsert never removes the old id, so it
     * lingers forever as a phantom duplicate once the override ends.
     */
    @Query("DELETE FROM treatments WHERE eventType IN (:eventTypes) AND dateMillis >= :sinceMillis AND id NOT IN (:keepIds)")
    suspend fun deleteStaleAdjustments(eventTypes: List<String>, sinceMillis: Long, keepIds: List<String>)
}
