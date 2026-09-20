package com.trionsandroid.app.data.settings

data class AlarmThreshold(
    val enabled: Boolean,
    val thresholdMgDl: Int,
)

/** The selectable "no new data" limits, in minutes. */
val NO_DATA_MINUTES_OPTIONS = listOf(20, 40, 60)

data class AlarmSettings(
    val alarmsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val urgentLow: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 55),
    val low: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 70),
    val high: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 180),
    val urgentHigh: AlarmThreshold = AlarmThreshold(enabled = true, thresholdMgDl = 250),
    /** Fires when glucose is in range but has been climbing slowly and steadily toward the high
     *  threshold for the past hour with no bolus/carbs logged — see PredictedHighEvaluator. Off by
     *  default. */
    val predictedHighEnabled: Boolean = false,
    /** Fires when no new glucose reading has arrived for [noDataMinutes]. Off by default. */
    val noDataEnabled: Boolean = false,
    /** One of [NO_DATA_MINUTES_OPTIONS]. */
    val noDataMinutes: Int = 20,
    /** Alarm notifications stay on screen (can't be swiped away) until OK is pressed or tapped. */
    val requireAcknowledgement: Boolean = false,
    /** Only meaningful alongside [requireAcknowledgement]: re-fires an unacknowledged alarm every
     *  few minutes rather than posting it once and leaving it sitting there silently. */
    val repeatIfNotAcknowledged: Boolean = false,
)
