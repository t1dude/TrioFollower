package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseThresholdAlarm

enum class AlarmZone(val displayTitle: String) {
    URGENT_LOW("Urgent low"),
    LOW("Low"),
    NORMAL("In range"),
    PREDICTED_HIGH("Predicted high"),
    HIGH("High"),
    URGENT_HIGH("Urgent high"),
    NO_DATA("No data"),
}

/**
 * Most severe first. A tier that's disabled, or not allowed to fire right now (see
 * [isAllowedNow]), falls through to the next one.
 */
fun evaluateAlarmZone(mgDl: Int, alarms: AlarmSettings, nowMinuteOfDay: Int): AlarmZone = when {
    alarms.urgentLow.isArmed(alarms, nowMinuteOfDay) && mgDl <= alarms.urgentLow.thresholdMgDl -> AlarmZone.URGENT_LOW
    alarms.low.isArmed(alarms, nowMinuteOfDay) && mgDl <= alarms.low.thresholdMgDl -> AlarmZone.LOW
    alarms.urgentHigh.isArmed(alarms, nowMinuteOfDay) && mgDl >= alarms.urgentHigh.thresholdMgDl -> AlarmZone.URGENT_HIGH
    alarms.high.isArmed(alarms, nowMinuteOfDay) && mgDl >= alarms.high.thresholdMgDl -> AlarmZone.HIGH
    else -> AlarmZone.NORMAL
}

private fun GlucoseThresholdAlarm.isArmed(alarms: AlarmSettings, nowMinuteOfDay: Int) =
    enabled && isAllowedNow(behavior, alarms.dayNightWindow, nowMinuteOfDay)

/** The behavior (day/night, sound, vibration, acknowledgement) that governs a given zone's alarm. */
fun AlarmSettings.behaviorFor(zone: AlarmZone): AlarmBehavior = when (zone) {
    AlarmZone.URGENT_LOW -> urgentLow.behavior
    AlarmZone.LOW -> low.behavior
    AlarmZone.HIGH -> high.behavior
    AlarmZone.URGENT_HIGH -> urgentHigh.behavior
    AlarmZone.PREDICTED_HIGH -> predictedHigh.behavior
    AlarmZone.NO_DATA -> noData.behavior
    AlarmZone.NORMAL -> AlarmBehavior()
}
