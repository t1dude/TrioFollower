package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmSettings

enum class AlarmZone(val displayTitle: String) {
    URGENT_LOW("Urgent low"),
    LOW("Low"),
    NORMAL("In range"),
    PREDICTED_HIGH("Predicted high"),
    HIGH("High"),
    URGENT_HIGH("Urgent high"),
}

/**
 * Most-severe-first, skipping any tier the user has disabled — a disabled tier falls through to
 * the next enclosing one rather than being treated as "in range" (e.g. disabling "low" while
 * "urgent low" stays on still catches an urgent-low reading).
 */
fun evaluateAlarmZone(mgDl: Int, alarms: AlarmSettings): AlarmZone = when {
    alarms.urgentLow.enabled && mgDl <= alarms.urgentLow.thresholdMgDl -> AlarmZone.URGENT_LOW
    alarms.low.enabled && mgDl <= alarms.low.thresholdMgDl -> AlarmZone.LOW
    alarms.urgentHigh.enabled && mgDl >= alarms.urgentHigh.thresholdMgDl -> AlarmZone.URGENT_HIGH
    alarms.high.enabled && mgDl >= alarms.high.thresholdMgDl -> AlarmZone.HIGH
    else -> AlarmZone.NORMAL
}
