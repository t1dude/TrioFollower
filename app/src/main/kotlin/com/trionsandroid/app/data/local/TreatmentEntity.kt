package com.trionsandroid.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey val id: String,
    val dateMillis: Long,
    val eventType: String,
    val insulinUnits: Double?,
    val carbsGrams: Double?,
    val durationMinutes: Double?,
    val basalRateUnitsPerHour: Double?,
)
