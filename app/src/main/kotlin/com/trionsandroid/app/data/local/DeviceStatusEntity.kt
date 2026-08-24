package com.trionsandroid.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_status")
data class DeviceStatusEntity(
    @PrimaryKey val id: String,
    val dateMillis: Long,
    val iobUnits: Double?,
    val cobGrams: Double?,
)
