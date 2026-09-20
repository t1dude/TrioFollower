package com.trionsandroid.app.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setNightscoutUrl(url: String)
    suspend fun setGlucoseUnit(unit: GlucoseUnit)
    suspend fun setTimeFormat(format: TimeFormat)
    suspend fun setGlucoseColorScheme(scheme: GlucoseColorScheme)
    suspend fun setHomeStatsFace(face: HomeStatsFace)
    suspend fun setForecastDisplay(display: ForecastDisplay)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setRefreshIntervalMinutes(minutes: Int)
    suspend fun setBackgroundMode(mode: BackgroundMode)
    suspend fun setAlarmSettings(alarms: AlarmSettings)
}
