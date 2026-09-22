package com.trionsandroid.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
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

    // AlarmSettings has grown a Day/Night window plus a per-alarm sound/vibration/acknowledgement
    // behavior on every one of its ~14 alarms; flattening that into individual preference keys (as
    // every other setting here does) would mean 100+ keys kept in sync by hand. It's serialized as
    // one JSON blob instead - ignoreUnknownKeys/lenient (see NetworkModule) keeps old and new app
    // versions from breaking each other as more alarms are added.
    override suspend fun setAlarmSettings(alarms: AlarmSettings) {
        dataStore.edit { it[Keys.ALARMS_JSON] = json.encodeToString(AlarmSettings.serializer(), alarms) }
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        val unit = this[Keys.GLUCOSE_UNIT]?.let { runCatching { GlucoseUnit.valueOf(it) }.getOrNull() }
            ?: defaults.glucoseUnit
        val timeFormat = this[Keys.TIME_FORMAT]?.let { runCatching { TimeFormat.valueOf(it) }.getOrNull() }
            ?: defaults.timeFormat
        val mode = this[Keys.BACKGROUND_MODE]?.let { runCatching { BackgroundMode.valueOf(it) }.getOrNull() }
            ?: defaults.backgroundMode
        val alarms = this[Keys.ALARMS_JSON]
            ?.let { runCatching { json.decodeFromString(AlarmSettings.serializer(), it) }.getOrNull() }
            ?: defaults.alarms
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
            alarms = alarms,
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
        val ALARMS_JSON = stringPreferencesKey("alarms_json")
    }
}
