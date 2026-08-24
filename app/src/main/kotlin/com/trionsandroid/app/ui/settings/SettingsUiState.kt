package com.trionsandroid.app.ui.settings

import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.GlucoseUnit

data class SettingsUiState(
    val nightscoutUrl: String = "",
    val accessToken: String = "",
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val refreshIntervalMinutes: Int = 5,
    val backgroundMode: BackgroundMode = BackgroundMode.WORK_MANAGER,
    val alarms: AlarmSettings = AlarmSettings(),
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle,
)
