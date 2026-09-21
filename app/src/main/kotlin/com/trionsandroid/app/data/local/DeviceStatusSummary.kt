package com.trionsandroid.app.data.local

/** The device status columns the UI lists need, without the large reason and forecast text. */
data class DeviceStatusSummary(
    val id: String,
    val dateMillis: Long,
    val iobUnits: Double?,
    val cobGrams: Double?,
    val reservoirUnits: Double?,
    val eventualBgMgDl: Int?,
)
