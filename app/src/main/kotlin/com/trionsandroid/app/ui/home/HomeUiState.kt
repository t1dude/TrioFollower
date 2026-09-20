package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.DeviceStatusPoint
import com.trionsandroid.app.data.nightscout.Forecast
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
import com.trionsandroid.app.data.settings.TimeFormat

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Incremented when a refresh finishes; the chart scrolls to the newest data.
    val refreshCount: Int = 0,
    // True for open/pull refreshes (the chart jumps to the latest data even if panned away), false for automatic ones.
    val forceScrollToLatest: Boolean = true,
    val glucoseColorScheme: GlucoseColorScheme = GlucoseColorScheme.DYNAMIC,
    val homeStatsFace: HomeStatsFace = HomeStatsFace.TIME_IN_RANGE,
    val forecast: Forecast? = null,
    val forecastDisplay: ForecastDisplay = ForecastDisplay.CONE,
    val refreshIntervalMinutes: Int = 5,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MMOL_L,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val alarms: AlarmSettings = AlarmSettings(),
    val readings: List<GlucoseReading> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val insulinProfile: InsulinProfile? = null,
    val deviceStatusPoints: List<DeviceStatusPoint> = emptyList(),
)
