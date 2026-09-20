package com.trionsandroid.app.data.settings

data class UserSettings(
    val nightscoutUrl: String = "",
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    // 24-hour default preserves existing behavior for anyone who doesn't touch the setting —
    // every timestamp already displayed as HH:mm before this setting existed.
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    // Off by default: holding the screen on drains battery, so it has to be a deliberate choice.
    val keepScreenOn: Boolean = false,
    // Cone matches Trio's own default forecast display.
    val forecastDisplay: ForecastDisplay = ForecastDisplay.CONE,
    val refreshIntervalMinutes: Int = 5,
    val backgroundMode: BackgroundMode = BackgroundMode.WORK_MANAGER,
    val alarms: AlarmSettings = AlarmSettings(),
)
