package com.trionsandroid.app.data.settings

data class AlarmThreshold(
    val enabled: Boolean,
    val thresholdMgDl: Int,
)

data class AlarmSettings(
    val alarmsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val urgentLow: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 55),
    val low: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 70),
    val high: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 180),
    val urgentHigh: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 250),
    /** Alarm notifications stay on screen (can't be swiped away) until OK is pressed or tapped. */
    val requireAcknowledgement: Boolean = false,
    /** Only meaningful alongside [requireAcknowledgement]: re-fires an unacknowledged alarm every
     *  few minutes rather than posting it once and leaving it sitting there silently. */
    val repeatIfNotAcknowledged: Boolean = false,
)
