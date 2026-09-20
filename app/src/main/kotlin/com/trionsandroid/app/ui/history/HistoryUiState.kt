package com.trionsandroid.app.ui.history

import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat

data class HistoryUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val alarms: AlarmSettings = AlarmSettings(),
    val glucoseColorScheme: GlucoseColorScheme = GlucoseColorScheme.DYNAMIC,
    val readings: List<GlucoseReading> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
)
