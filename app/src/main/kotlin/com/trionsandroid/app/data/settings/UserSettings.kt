package com.trionsandroid.app.data.settings

data class UserSettings(
    val nightscoutUrl: String = "",
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val refreshIntervalMinutes: Int = 5,
    val backgroundMode: BackgroundMode = BackgroundMode.WORK_MANAGER,
    val alarms: AlarmSettings = AlarmSettings(),
)
