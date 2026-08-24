package com.trionsandroid.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glucose_entries")
data class GlucoseEntryEntity(
    @PrimaryKey val id: String,
    val dateMillis: Long,
    val sgvMgDl: Int,
    val direction: String?,
    val device: String?,
)
