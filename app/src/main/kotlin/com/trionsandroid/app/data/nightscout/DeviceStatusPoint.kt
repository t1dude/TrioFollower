package com.trionsandroid.app.data.nightscout

import java.time.Instant

data class DeviceStatusPoint(
    val timestamp: Instant,
    val iobUnits: Double?,
    val cobGrams: Double?,
    // Double.POSITIVE_INFINITY means "50 U or more, level unknown" (shown as "50+").
    val reservoirUnits: Double?,
    /** The loop's eventual glucose prediction (mg/dL). */
    val eventualBgMgDl: Int? = null,
)
