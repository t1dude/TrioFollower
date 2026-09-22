package com.trionsandroid.app.data.settings

data class AlarmThreshold(
    val enabled: Boolean,
    val thresholdMgDl: Int,
)

/** The selectable "no new data" limits, in minutes. */
val NO_DATA_MINUTES_OPTIONS = listOf(20, 40, 60)

/** The selectable "not looping" limits, in minutes. */
val NOT_LOOPING_MINUTES_OPTIONS = listOf(20, 40, 60)

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

    // --- Additional Alarms: each is independent of the glucose zone and of the others. ---

    /** Alert when insulin on board is at or above [iobThresholdUnits]. Off by default. */
    val iobAlarmEnabled: Boolean = false,
    val iobThresholdUnits: Double = 5.0,
    /** Alert when carbs on board is at or above [cobThresholdGrams]. Off by default. */
    val cobAlarmEnabled: Boolean = false,
    val cobThresholdGrams: Double = 30.0,
    /** Alert when the pump reservoir is at or below [reservoirThresholdUnits]. Off by default. */
    val reservoirAlarmEnabled: Boolean = false,
    val reservoirThresholdUnits: Double = 20.0,
    /** Alert when time left on the CGM sensor is at or below [sensorChangeHoursThreshold]. Off by default. */
    val sensorChangeAlarmEnabled: Boolean = false,
    val sensorChangeHoursThreshold: Int = 8,
    /** Alert when time left on the pump site is at or below [pumpChangeHoursThreshold]. Off by default. */
    val pumpChangeAlarmEnabled: Boolean = false,
    val pumpChangeHoursThreshold: Int = 8,
    /** Alert when no confirmed loop (algorithm reasoning) for [notLoopingMinutes]. Off by default. */
    val notLoopingAlarmEnabled: Boolean = false,
    val notLoopingMinutes: Int = 20,
    /**
     * Alert when the Trio (looping) phone's own battery, as it reports itself to Nightscout, drops
     * to or below [lowPhoneBatteryPercent]. Not the battery of the phone this app runs on. Off by default.
     */
    val lowPhoneBatteryAlarmEnabled: Boolean = false,
    val lowPhoneBatteryPercent: Int = 20,
    /**
     * For when the other alarms aren't quite enough alarm fatigue: fires at a few random times a
     * day (up to 4), for no reason at all. No threshold to set. Off by default.
     */
    val randomAlarmEnabled: Boolean = false,
)
