package com.trionsandroid.app.data.nightscout

import java.time.Instant

// Rotation as in Trio's CurrentGlucoseView.
enum class GlucoseTrend(val arrow: String, val rotationDegrees: Float) {
    DoubleUp("⇈", -90f),
    SingleUp("↑", -90f),
    FortyFiveUp("↗", -45f),
    Flat("→", 0f),
    FortyFiveDown("↘", 45f),
    SingleDown("↓", 90f),
    DoubleDown("⇊", 90f),
    NotComputable("?", 0f),
    RateOutOfRange("?", 0f);

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
