package com.trionsandroid.app.data.nightscout

import java.time.Instant

data class DeviceStatusPoint(
    val timestamp: Instant,
    val iobUnits: Double?,
    val cobGrams: Double?,
    // Double.POSITIVE_INFINITY is a sentinel for the 0xDEADBEEF value some pumps report meaning
    // "at least 50U, exact level unknown" (see PumpStatusDto's doc comment) — display as "50+".
    val reservoirUnits: Double?,
)
