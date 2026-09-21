package com.trionsandroid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import com.trionsandroid.app.data.nightscout.ConnectionEvents
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.update.UpdateCheckResult
import com.trionsandroid.app.data.update.UpdateChecker
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.BolusDisplayThreshold
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
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
    private val connectionEvents: ConnectionEvents,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    // Text fields are driven by these local flows, not by DataStore: a value that round-trips
    // through disk lags a frame and resets the cursor.
    private val nightscoutUrlDraft = MutableStateFlow("")
    private val accessToken = MutableStateFlow("")
    private val connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    private val updateStatus = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        nightscoutUrlDraft,
        accessToken,
        connectionTestState,
        updateStatus,
    ) { settings, url, token, testState, updateStatusText ->
        SettingsUiState(
            nightscoutUrl = url,
            accessToken = token,
            glucoseUnit = settings.glucoseUnit,
            timeFormat = settings.timeFormat,
            keepScreenOn = settings.keepScreenOn,
            showNowLine = settings.showNowLine,
            bolusDisplayThreshold = settings.bolusDisplayThreshold,
            checkForUpdates = settings.checkForUpdates,
            updateStatus = updateStatusText,
            glucoseColorScheme = settings.glucoseColorScheme,
            homeStatsFace = settings.homeStatsFace,
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

    fun onGlucoseColorSchemeChange(scheme: GlucoseColorScheme) {
        viewModelScope.launch { settingsRepository.setGlucoseColorScheme(scheme) }
    }

    fun onHomeStatsFaceChange(face: HomeStatsFace) {
        viewModelScope.launch { settingsRepository.setHomeStatsFace(face) }
    }

    fun onForecastDisplayChange(display: ForecastDisplay) {
        viewModelScope.launch { settingsRepository.setForecastDisplay(display) }
    }

    fun onCheckForUpdatesChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCheckForUpdates(enabled) }
    }

    fun checkForUpdatesNow() {
        updateStatus.value = "Checking..."
        viewModelScope.launch {
            updateStatus.value = when (val result = updateChecker.checkNow()) {
                is UpdateCheckResult.Available -> "Version ${result.info.version} is available. See the card on the Home screen."
                UpdateCheckResult.UpToDate -> "You have the latest version."
                UpdateCheckResult.Failed -> "Couldn't check for updates. Try again later."
            }
        }
    }

    fun onBolusDisplayThresholdChange(threshold: BolusDisplayThreshold) {
        viewModelScope.launch { settingsRepository.setBolusDisplayThreshold(threshold) }
    }

    fun onShowNowLineChange(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowNowLine(show) }
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
        // Leaves refreshIntervalMinutes alone even if it isn't valid for the new mode; clamping here
        // used to overwrite the user's choice. BackgroundSyncScheduler coerces it when applying.
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
                // Lets Home clear its "set up Nightscout" error and sync right away.
                connectionEvents.notifyEstablished()
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
