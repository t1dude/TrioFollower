package com.trionsandroid.app.data.nightscout

import java.time.Instant

data class DeviceStatusPoint(
    val timestamp: Instant,
    val iobUnits: Double?,
    val cobGrams: Double?,
)
