package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmSettings

enum class AlarmZone(val displayTitle: String) {
    URGENT_LOW("Urgent low"),
    LOW("Low"),
    NORMAL("In range"),
    PREDICTED_HIGH("Predicted high"),
    HIGH("High"),
    URGENT_HIGH("Urgent high"),
    NO_DATA("No data"),
}

/** Most severe first. A disabled tier falls through to the next one. */
fun evaluateAlarmZone(mgDl: Int, alarms: AlarmSettings): AlarmZone = when {
    alarms.urgentLow.enabled && mgDl <= alarms.urgentLow.thresholdMgDl -> AlarmZone.URGENT_LOW
    alarms.low.enabled && mgDl <= alarms.low.thresholdMgDl -> AlarmZone.LOW
    alarms.urgentHigh.enabled && mgDl >= alarms.urgentHigh.thresholdMgDl -> AlarmZone.URGENT_HIGH
    alarms.high.enabled && mgDl >= alarms.high.thresholdMgDl -> AlarmZone.HIGH
    else -> AlarmZone.NORMAL
}
