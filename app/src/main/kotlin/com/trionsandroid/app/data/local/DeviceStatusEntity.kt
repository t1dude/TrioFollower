package com.trionsandroid.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_status")
data class DeviceStatusEntity(
    @PrimaryKey val id: String,
    val dateMillis: Long,
    val iobUnits: Double?,
    val cobGrams: Double?,
    val reservoirUnits: Double?,
    val reason: String? = null,
    // Forecast curves as comma-separated mg/dL values; forecastStartMillis is non-null only when
    // at least one curve is present.
    val forecastStartMillis: Long? = null,
    val predIob: String? = null,
    val predZt: String? = null,
    val predCob: String? = null,
    val predUam: String? = null,
)
