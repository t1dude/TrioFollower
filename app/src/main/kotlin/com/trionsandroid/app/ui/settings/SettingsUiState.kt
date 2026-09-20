package com.trionsandroid.app.ui.settings

import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
import com.trionsandroid.app.data.settings.TimeFormat

data class SettingsUiState(
    val nightscoutUrl: String = "",
    val accessToken: String = "",
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val keepScreenOn: Boolean = false,
    val showNowLine: Boolean = true,
    val glucoseColorScheme: GlucoseColorScheme = GlucoseColorScheme.DYNAMIC,
    val homeStatsFace: HomeStatsFace = HomeStatsFace.TIME_IN_RANGE,
    val forecastDisplay: ForecastDisplay = ForecastDisplay.CONE,
    val refreshIntervalMinutes: Int = 5,
    val backgroundMode: BackgroundMode = BackgroundMode.WORK_MANAGER,
    val alarms: AlarmSettings = AlarmSettings(),
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
)
