package com.trionsandroid.app.data.settings

data class UserSettings(
    val nightscoutUrl: String = "",
    val welcomeCompleted: Boolean = false,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    // Off by default: it costs battery.
    val keepScreenOn: Boolean = false,
    // Vertical line marking the current time on the chart; on by default, as in Trio.
    val showNowLine: Boolean = true,
    // Widget background: 0 = solid black, 100 = fully transparent.
    val widgetTransparencyPercent: Int = 0,
    // Cone is Trio's default.
    val glucoseColorScheme: GlucoseColorScheme = GlucoseColorScheme.DYNAMIC,
    val homeStatsFace: HomeStatsFace = HomeStatsFace.TIME_IN_RANGE,
    val forecastDisplay: ForecastDisplay = ForecastDisplay.CONE,
    val refreshIntervalMinutes: Int = 5,
    val backgroundMode: BackgroundMode = BackgroundMode.WORK_MANAGER,
    val alarms: AlarmSettings = AlarmSettings(),
)
