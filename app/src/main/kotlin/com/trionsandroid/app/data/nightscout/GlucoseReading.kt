package com.trionsandroid.app.data.nightscout

import java.time.Instant

enum class GlucoseTrend(val arrow: String) {
    DoubleUp("⇈"),
    SingleUp("↑"),
    FortyFiveUp("↗"),
    Flat("→"),
    FortyFiveDown("↘"),
    SingleDown("↓"),
    DoubleDown("⇊"),
    NotComputable("?"),
    RateOutOfRange("?");

    companion object {
        private val byDirection = entries.associateBy { it.name }

        fun fromDirection(direction: String?): GlucoseTrend =
            direction?.let { byDirection[it] } ?: NotComputable
    }
}

data class GlucoseReading(
    val id: String,
    val timestamp: Instant,
    val mgDl: Int,
    val trend: GlucoseTrend,
)
