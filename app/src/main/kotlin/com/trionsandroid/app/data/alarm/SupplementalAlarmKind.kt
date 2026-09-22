package com.trionsandroid.app.data.alarm

/**
 * Alarms that are independent of the glucose zone (see [AlarmZone]) and of each other: any number
 * of these can be active at the same time, each with its own notification and acknowledgement.
 */
enum class SupplementalAlarmKind(val displayTitle: String, val notificationId: Int) {
    IOB_HIGH("IOB high", 1010),
    COB_HIGH("COB high", 1011),
    RESERVOIR_LOW("Reservoir low", 1012),
    SENSOR_CHANGE_DUE("Sensor change due", 1013),
    PUMP_CHANGE_DUE("Pump change due", 1014),
    NOT_LOOPING("Not looping", 1015),
    /** The Trio (looping) phone's own battery, as it reports itself to Nightscout — not this app's phone. */
    LOW_PHONE_BATTERY("Low Trio phone battery", 1016),
}
