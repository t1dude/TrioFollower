package com.trionsandroid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.SecureTokenStore
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.settings.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureTokenStore: SecureTokenStore,
    private val serviceFactory: NightscoutServiceFactory,
    private val diagnosticLogger: DiagnosticLogger,
) : ViewModel() {

    // Text fields need a locally-owned, synchronously-updated source of truth for their
    // displayed value. Driving a TextField's value straight from a Flow that round-trips
    // through disk I/O (DataStore/EncryptedSharedPreferences) lags by a frame on every
    // keystroke, which makes Compose treat each recomposition as an external edit and
    // reset the cursor to the start.
    private val nightscoutUrlDraft = MutableStateFlow("")
    private val accessToken = MutableStateFlow("")
    private val connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        nightscoutUrlDraft,
        accessToken,
        connectionTestState,
    ) { settings, url, token, testState ->
        SettingsUiState(
            nightscoutUrl = url,
            accessToken = token,
            glucoseUnit = settings.glucoseUnit,
            timeFormat = settings.timeFormat,
            keepScreenOn = settings.keepScreenOn,
            forecastDisplay = settings.forecastDisplay,
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            backgroundMode = settings.backgroundMode,
            alarms = settings.alarms,
            connectionTestState = testState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            nightscoutUrlDraft.value = settingsRepository.settings.first().nightscoutUrl
            accessToken.value = secureTokenStore.getAccessToken()
        }
    }

    fun onNightscoutUrlChange(url: String) {
        nightscoutUrlDraft.value = url
        connectionTestState.value = ConnectionTestState.Idle
        viewModelScope.launch { settingsRepository.setNightscoutUrl(url) }
    }

    fun onAccessTokenChange(token: String) {
        accessToken.value = token
        connectionTestState.value = ConnectionTestState.Idle
        viewModelScope.launch { secureTokenStore.setAccessToken(token) }
    }

    fun onGlucoseUnitChange(unit: GlucoseUnit) {
        viewModelScope.launch { settingsRepository.setGlucoseUnit(unit) }
    }

    fun onForecastDisplayChange(display: ForecastDisplay) {
        viewModelScope.launch { settingsRepository.setForecastDisplay(display) }
    }

    fun onKeepScreenOnChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun onTimeFormatChange(format: TimeFormat) {
        viewModelScope.launch { settingsRepository.setTimeFormat(format) }
    }

    fun onRefreshIntervalChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setRefreshIntervalMinutes(minutes) }
    }

    fun onBackgroundModeChange(mode: BackgroundMode) {
        // Deliberately doesn't touch refreshIntervalMinutes here, even if the current value isn't
        // in mode.allowedRefreshIntervals() for the newly selected mode. This used to clamp-and-
        // persist a default (e.g. 15) on every switch, which permanently overwrote a value like
        // "5" the user had chosen for Real-time — since 15 is *also* valid for Real-time, that
        // overwrite never self-corrected on switching back, silently losing the real preference.
        // BackgroundSyncScheduler.apply() already defensively coerces for WorkManager's 15-minute
        // floor, so no schedule ever actually runs at an invalid interval; only the segmented
        // button's selection state is affected, and it's fine for it to show nothing selected
        // until the user explicitly picks an interval for the newly active mode.
        viewModelScope.launch { settingsRepository.setBackgroundMode(mode) }
    }

    fun onAlarmSettingsChange(alarms: AlarmSettings) {
        viewModelScope.launch { settingsRepository.setAlarmSettings(alarms) }
    }

    fun logFile(): File = diagnosticLogger.file()

    fun clearLog() = diagnosticLogger.clear()

    fun testConnection() {
        val url = uiState.value.nightscoutUrl
        val token = uiState.value.accessToken
        if (url.isBlank() || token.isBlank()) {
            connectionTestState.value = ConnectionTestState.Error("Enter a Nightscout URL and access token first")
            return
        }
        connectionTestState.value = ConnectionTestState.Loading
        viewModelScope.launch {
            connectionTestState.value = try {
                serviceFactory.authApi(url).requestAuthorization(token)
                ConnectionTestState.Success
            } catch (e: HttpException) {
                ConnectionTestState.Error("Nightscout rejected the request (HTTP ${e.code()}) — check the URL and token")
            } catch (e: IOException) {
                ConnectionTestState.Error("Couldn't reach that URL — check your connection and the address")
            } catch (e: IllegalArgumentException) {
                ConnectionTestState.Error("That doesn't look like a valid URL")
            }
        }
    }
}
