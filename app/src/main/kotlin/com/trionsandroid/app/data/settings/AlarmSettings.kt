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
    /** Alert on a slow, steady in-range climb toward the high threshold (see PredictedHighEvaluator). Off by default. */
    val predictedHighEnabled: Boolean = false,
    /** Alert when no new glucose arrives for [noDataMinutes]. Off by default. */
    val noDataEnabled: Boolean = false,
    val noDataMinutes: Int = 20,
    /** Notifications can't be swiped away until acknowledged. */
    val requireAcknowledgement: Boolean = false,
    /** With [requireAcknowledgement], re-alert until acknowledged. */
    val repeatIfNotAcknowledged: Boolean = false,
)
