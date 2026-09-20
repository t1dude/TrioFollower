package com.trionsandroid.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data.map { it.toUserSettings() }

    override suspend fun setNightscoutUrl(url: String) {
        dataStore.edit { it[Keys.NIGHTSCOUT_URL] = url.trim() }
    }

    override suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        dataStore.edit { it[Keys.GLUCOSE_UNIT] = unit.name }
    }

    override suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    override suspend fun setRefreshIntervalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.REFRESH_INTERVAL_MINUTES] = minutes }
    }

    override suspend fun setBackgroundMode(mode: BackgroundMode) {
        dataStore.edit { it[Keys.BACKGROUND_MODE] = mode.name }
    }

    override suspend fun setAlarmSettings(alarms: AlarmSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.ALARMS_ENABLED] = alarms.alarmsEnabled
            prefs[Keys.ALARM_SOUND_ENABLED] = alarms.soundEnabled
            prefs[Keys.ALARM_VIBRATION_ENABLED] = alarms.vibrationEnabled
            prefs[Keys.URGENT_LOW_ENABLED] = alarms.urgentLow.enabled
            prefs[Keys.URGENT_LOW_THRESHOLD_MGDL] = alarms.urgentLow.thresholdMgDl
            prefs[Keys.LOW_ENABLED] = alarms.low.enabled
            prefs[Keys.LOW_THRESHOLD_MGDL] = alarms.low.thresholdMgDl
            prefs[Keys.HIGH_ENABLED] = alarms.high.enabled
            prefs[Keys.HIGH_THRESHOLD_MGDL] = alarms.high.thresholdMgDl
            prefs[Keys.URGENT_HIGH_ENABLED] = alarms.urgentHigh.enabled
            prefs[Keys.URGENT_HIGH_THRESHOLD_MGDL] = alarms.urgentHigh.thresholdMgDl
            prefs[Keys.NO_DATA_ENABLED] = alarms.noDataEnabled
            prefs[Keys.NO_DATA_MINUTES] = alarms.noDataMinutes
            prefs[Keys.PREDICTED_HIGH_ENABLED] = alarms.predictedHighEnabled
            prefs[Keys.ALARM_REQUIRE_ACKNOWLEDGEMENT] = alarms.requireAcknowledgement
            prefs[Keys.ALARM_REPEAT_IF_NOT_ACKNOWLEDGED] = alarms.repeatIfNotAcknowledged
        }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        val unit = this[Keys.GLUCOSE_UNIT]?.let { runCatching { GlucoseUnit.valueOf(it) }.getOrNull() }
            ?: defaults.glucoseUnit
        val timeFormat = this[Keys.TIME_FORMAT]?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() }
            ?: defaults.timeFormat
        val mode = this[Keys.BACKGROUND_MODE]?.let { runCatching { BackgroundMode.valueOf(it) }.getOrNull() }
            ?: defaults.backgroundMode
        val defaultAlarms = AlarmSettings()
        return UserSettings(
            nightscoutUrl = this[Keys.NIGHTSCOUT_URL] ?: defaults.nightscoutUrl,
            glucoseUnit = unit,
            timeFormat = timeFormat,
            refreshIntervalMinutes = this[Keys.REFRESH_INTERVAL_MINUTES] ?: defaults.refreshIntervalMinutes,
            backgroundMode = mode,
            alarms = AlarmSettings(
                alarmsEnabled = this[Keys.ALARMS_ENABLED] ?: defaultAlarms.alarmsEnabled,
                soundEnabled = this[Keys.ALARM_SOUND_ENABLED] ?: defaultAlarms.soundEnabled,
                vibrationEnabled = this[Keys.ALARM_VIBRATION_ENABLED] ?: defaultAlarms.vibrationEnabled,
                urgentLow = AlarmThreshold(
                    enabled = this[Keys.URGENT_LOW_ENABLED] ?: defaultAlarms.urgentLow.enabled,
                    thresholdMgDl = this[Keys.URGENT_LOW_THRESHOLD_MGDL] ?: defaultAlarms.urgentLow.thresholdMgDl,
                ),
                low = AlarmThreshold(
                    enabled = this[Keys.LOW_ENABLED] ?: defaultAlarms.low.enabled,
                    thresholdMgDl = this[Keys.LOW_THRESHOLD_MGDL] ?: defaultAlarms.low.thresholdMgDl,
                ),
                high = AlarmThreshold(
                    enabled = this[Keys.HIGH_ENABLED] ?: defaultAlarms.high.enabled,
                    thresholdMgDl = this[Keys.HIGH_THRESHOLD_MGDL] ?: defaultAlarms.high.thresholdMgDl,
                ),
                urgentHigh = AlarmThreshold(
                    enabled = this[Keys.URGENT_HIGH_ENABLED] ?: defaultAlarms.urgentHigh.enabled,
                    thresholdMgDl = this[Keys.URGENT_HIGH_THRESHOLD_MGDL] ?: defaultAlarms.urgentHigh.thresholdMgDl,
                ),
                noDataEnabled = this[Keys.NO_DATA_ENABLED] ?: defaultAlarms.noDataEnabled,
                noDataMinutes = this[Keys.NO_DATA_MINUTES]?.takeIf { it in NO_DATA_MINUTES_OPTIONS }
                    ?: defaultAlarms.noDataMinutes,
                predictedHighEnabled = this[Keys.PREDICTED_HIGH_ENABLED] ?: defaultAlarms.predictedHighEnabled,
                requireAcknowledgement = this[Keys.ALARM_REQUIRE_ACKNOWLEDGEMENT]
                    ?: defaultAlarms.requireAcknowledgement,
                repeatIfNotAcknowledged = this[Keys.ALARM_REPEAT_IF_NOT_ACKNOWLEDGED]
                    ?: defaultAlarms.repeatIfNotAcknowledged,
            ),
        )
    }

    private object Keys {
        val NIGHTSCOUT_URL = stringPreferencesKey("nightscout_url")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
        val BACKGROUND_MODE = stringPreferencesKey("background_mode")
        val ALARMS_ENABLED = booleanPreferencesKey("alarms_enabled")
        val ALARM_SOUND_ENABLED = booleanPreferencesKey("alarm_sound_enabled")
        val ALARM_VIBRATION_ENABLED = booleanPreferencesKey("alarm_vibration_enabled")
        val URGENT_LOW_ENABLED = booleanPreferencesKey("urgent_low_enabled")
        val URGENT_LOW_THRESHOLD_MGDL = intPreferencesKey("urgent_low_threshold_mgdl")
        val LOW_ENABLED = booleanPreferencesKey("low_enabled")
        val LOW_THRESHOLD_MGDL = intPreferencesKey("low_threshold_mgdl")
        val HIGH_ENABLED = booleanPreferencesKey("high_enabled")
        val HIGH_THRESHOLD_MGDL = intPreferencesKey("high_threshold_mgdl")
        val URGENT_HIGH_ENABLED = booleanPreferencesKey("urgent_high_enabled")
        val URGENT_HIGH_THRESHOLD_MGDL = intPreferencesKey("urgent_high_threshold_mgdl")
        val NO_DATA_ENABLED = booleanPreferencesKey("no_data_enabled")
        val NO_DATA_MINUTES = intPreferencesKey("no_data_minutes")
        val PREDICTED_HIGH_ENABLED = booleanPreferencesKey("predicted_high_enabled")
        val ALARM_REQUIRE_ACKNOWLEDGEMENT = booleanPreferencesKey("alarm_require_acknowledgement")
        val ALARM_REPEAT_IF_NOT_ACKNOWLEDGED = booleanPreferencesKey("alarm_repeat_if_not_acknowledged")
    }
}
