package com.trionsandroid.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GlucoseEntryEntity::class, TreatmentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrioDatabase : RoomDatabase() {
    abstract fun glucoseEntryDao(): GlucoseEntryDao
    abstract fun treatmentDao(): TreatmentDao
}
