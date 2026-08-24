package com.trionsandroid.app.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setNightscoutUrl(url: String)
    suspend fun setGlucoseUnit(unit: GlucoseUnit)
    suspend fun setRefreshIntervalMinutes(minutes: Int)
    suspend fun setBackgroundMode(mode: BackgroundMode)
    suspend fun setAlarmSettings(alarms: AlarmSettings)
}
