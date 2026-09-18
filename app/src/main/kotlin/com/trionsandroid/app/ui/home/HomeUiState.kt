package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.DeviceStatusPoint
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val alarms: AlarmSettings = AlarmSettings(),
    val readings: List<GlucoseReading> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val insulinProfile: InsulinProfile? = null,
    val deviceStatusPoints: List<DeviceStatusPoint> = emptyList(),
)
