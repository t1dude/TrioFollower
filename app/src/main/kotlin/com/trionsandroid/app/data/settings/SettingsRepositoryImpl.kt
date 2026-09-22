package com.trionsandroid.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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

    override suspend fun setWelcomeCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.WELCOME_COMPLETED] = completed }
    }

    override suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        dataStore.edit { it[Keys.GLUCOSE_UNIT] = unit.name }
    }

    override suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    override suspend fun setGlucoseColorScheme(scheme: GlucoseColorScheme) {
        dataStore.edit { it[Keys.GLUCOSE_COLOR_SCHEME] = scheme.name }
    }

    override suspend fun setHomeStatsFace(face: HomeStatsFace) {
        dataStore.edit { it[Keys.HOME_STATS_FACE] = face.name }
    }

    override suspend fun setForecastDisplay(display: ForecastDisplay) {
        dataStore.edit { it[Keys.FORECAST_DISPLAY] = display.name }
    }

    override suspend fun setCheckForUpdates(enabled: Boolean) {
        dataStore.edit { it[Keys.CHECK_FOR_UPDATES] = enabled }
    }

    override suspend fun setBolusDisplayThreshold(threshold: BolusDisplayThreshold) {
        dataStore.edit { it[Keys.BOLUS_DISPLAY_THRESHOLD] = threshold.name }
    }

    override suspend fun setShowNowLine(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_NOW_LINE] = show }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
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
            prefs[Keys.IOB_ALARM_ENABLED] = alarms.iobAlarmEnabled
            prefs[Keys.IOB_THRESHOLD_UNITS] = alarms.iobThresholdUnits
            prefs[Keys.COB_ALARM_ENABLED] = alarms.cobAlarmEnabled
            prefs[Keys.COB_THRESHOLD_GRAMS] = alarms.cobThresholdGrams
            prefs[Keys.RESERVOIR_ALARM_ENABLED] = alarms.reservoirAlarmEnabled
            prefs[Keys.RESERVOIR_THRESHOLD_UNITS] = alarms.reservoirThresholdUnits
            prefs[Keys.SENSOR_CHANGE_ALARM_ENABLED] = alarms.sensorChangeAlarmEnabled
            prefs[Keys.SENSOR_CHANGE_HOURS_THRESHOLD] = alarms.sensorChangeHoursThreshold
            prefs[Keys.PUMP_CHANGE_ALARM_ENABLED] = alarms.pumpChangeAlarmEnabled
            prefs[Keys.PUMP_CHANGE_HOURS_THRESHOLD] = alarms.pumpChangeHoursThreshold
            prefs[Keys.NOT_LOOPING_ALARM_ENABLED] = alarms.notLoopingAlarmEnabled
            prefs[Keys.NOT_LOOPING_MINUTES] = alarms.notLoopingMinutes
            prefs[Keys.LOW_PHONE_BATTERY_ALARM_ENABLED] = alarms.lowPhoneBatteryAlarmEnabled
            prefs[Keys.LOW_PHONE_BATTERY_PERCENT] = alarms.lowPhoneBatteryPercent
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
            welcomeCompleted = this[Keys.WELCOME_COMPLETED] ?: defaults.welcomeCompleted,
            glucoseUnit = unit,
            timeFormat = timeFormat,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            showNowLine = this[Keys.SHOW_NOW_LINE] ?: defaults.showNowLine,
            checkForUpdates = this[Keys.CHECK_FOR_UPDATES] ?: defaults.checkForUpdates,
            bolusDisplayThreshold = this[Keys.BOLUS_DISPLAY_THRESHOLD]
                ?.let { runCatching { BolusDisplayThreshold.valueOf(it) }.getOrNull() } ?: defaults.bolusDisplayThreshold,
            glucoseColorScheme = this[Keys.GLUCOSE_COLOR_SCHEME]
                ?.let { runCatching { GlucoseColorScheme.valueOf(it) }.getOrNull() } ?: defaults.glucoseColorScheme,
            homeStatsFace = this[Keys.HOME_STATS_FACE]
                ?.let { runCatching { HomeStatsFace.valueOf(it) }.getOrNull() } ?: defaults.homeStatsFace,
            forecastDisplay = this[Keys.FORECAST_DISPLAY]?.let { runCatching { ForecastDisplay.valueOf(it) }.getOrNull() }
                ?: defaults.forecastDisplay,
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
                iobAlarmEnabled = this[Keys.IOB_ALARM_ENABLED] ?: defaultAlarms.iobAlarmEnabled,
                iobThresholdUnits = this[Keys.IOB_THRESHOLD_UNITS] ?: defaultAlarms.iobThresholdUnits,
                cobAlarmEnabled = this[Keys.COB_ALARM_ENABLED] ?: defaultAlarms.cobAlarmEnabled,
                cobThresholdGrams = this[Keys.COB_THRESHOLD_GRAMS] ?: defaultAlarms.cobThresholdGrams,
                reservoirAlarmEnabled = this[Keys.RESERVOIR_ALARM_ENABLED] ?: defaultAlarms.reservoirAlarmEnabled,
                reservoirThresholdUnits = this[Keys.RESERVOIR_THRESHOLD_UNITS]
                    ?: defaultAlarms.reservoirThresholdUnits,
                sensorChangeAlarmEnabled = this[Keys.SENSOR_CHANGE_ALARM_ENABLED]
                    ?: defaultAlarms.sensorChangeAlarmEnabled,
                sensorChangeHoursThreshold = this[Keys.SENSOR_CHANGE_HOURS_THRESHOLD]
                    ?: defaultAlarms.sensorChangeHoursThreshold,
                pumpChangeAlarmEnabled = this[Keys.PUMP_CHANGE_ALARM_ENABLED] ?: defaultAlarms.pumpChangeAlarmEnabled,
                pumpChangeHoursThreshold = this[Keys.PUMP_CHANGE_HOURS_THRESHOLD]
                    ?: defaultAlarms.pumpChangeHoursThreshold,
                notLoopingAlarmEnabled = this[Keys.NOT_LOOPING_ALARM_ENABLED] ?: defaultAlarms.notLoopingAlarmEnabled,
                notLoopingMinutes = this[Keys.NOT_LOOPING_MINUTES]?.takeIf { it in NOT_LOOPING_MINUTES_OPTIONS }
                    ?: defaultAlarms.notLoopingMinutes,
                lowPhoneBatteryAlarmEnabled = this[Keys.LOW_PHONE_BATTERY_ALARM_ENABLED]
                    ?: defaultAlarms.lowPhoneBatteryAlarmEnabled,
                lowPhoneBatteryPercent = this[Keys.LOW_PHONE_BATTERY_PERCENT]
                    ?: defaultAlarms.lowPhoneBatteryPercent,
            ),
        )
    }

    private object Keys {
        val WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        val NIGHTSCOUT_URL = stringPreferencesKey("nightscout_url")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val GLUCOSE_COLOR_SCHEME = stringPreferencesKey("glucose_color_scheme")
        val HOME_STATS_FACE = stringPreferencesKey("home_stats_face")
        val FORECAST_DISPLAY = stringPreferencesKey("forecast_display")
        val CHECK_FOR_UPDATES = booleanPreferencesKey("check_for_updates")
        val BOLUS_DISPLAY_THRESHOLD = stringPreferencesKey("bolus_display_threshold")
        val SHOW_NOW_LINE = booleanPreferencesKey("show_now_line")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
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
        val IOB_ALARM_ENABLED = booleanPreferencesKey("iob_alarm_enabled")
        val IOB_THRESHOLD_UNITS = doublePreferencesKey("iob_threshold_units")
        val COB_ALARM_ENABLED = booleanPreferencesKey("cob_alarm_enabled")
        val COB_THRESHOLD_GRAMS = doublePreferencesKey("cob_threshold_grams")
        val RESERVOIR_ALARM_ENABLED = booleanPreferencesKey("reservoir_alarm_enabled")
        val RESERVOIR_THRESHOLD_UNITS = doublePreferencesKey("reservoir_threshold_units")
        val SENSOR_CHANGE_ALARM_ENABLED = booleanPreferencesKey("sensor_change_alarm_enabled")
        val SENSOR_CHANGE_HOURS_THRESHOLD = intPreferencesKey("sensor_change_hours_threshold")
        val PUMP_CHANGE_ALARM_ENABLED = booleanPreferencesKey("pump_change_alarm_enabled")
        val PUMP_CHANGE_HOURS_THRESHOLD = intPreferencesKey("pump_change_hours_threshold")
        val NOT_LOOPING_ALARM_ENABLED = booleanPreferencesKey("not_looping_alarm_enabled")
        val NOT_LOOPING_MINUTES = intPreferencesKey("not_looping_minutes")
        val LOW_PHONE_BATTERY_ALARM_ENABLED = booleanPreferencesKey("low_phone_battery_alarm_enabled")
        val LOW_PHONE_BATTERY_PERCENT = intPreferencesKey("low_phone_battery_percent")
    }
}
