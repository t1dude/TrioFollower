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
    val eventualBgMgDl: Int? = null,
    // Forecast curves as comma-separated mg/dL; forecastStartMillis is set only when a curve exists.
    val forecastStartMillis: Long? = null,
    val predIob: String? = null,
    val predZt: String? = null,
    val predCob: String? = null,
    val predUam: String? = null,
)
