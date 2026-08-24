package com.trionsandroid.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.remote.NightscoutServiceFactory
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.SecureTokenStore
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.settings.allowedRefreshIntervals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val secureTokenStore: SecureTokenStore,
    private val serviceFactory: NightscoutServiceFactory,
) : ViewModel() {

    private val accessToken = MutableStateFlow("")
    private val connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        accessToken,
        connectionTestState,
    ) { settings, token, testState ->
        SettingsUiState(
            nightscoutUrl = settings.nightscoutUrl,
            accessToken = token,
            glucoseUnit = settings.glucoseUnit,
            refreshIntervalMinutes = settings.refreshIntervalMinutes,
            backgroundMode = settings.backgroundMode,
            alarms = settings.alarms,
            connectionTestState = testState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            accessToken.value = secureTokenStore.getAccessToken()
        }
    }

    fun onNightscoutUrlChange(url: String) {
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

    fun onRefreshIntervalChange(minutes: Int) {
        viewModelScope.launch { settingsRepository.setRefreshIntervalMinutes(minutes) }
    }

    fun onBackgroundModeChange(mode: BackgroundMode) {
        viewModelScope.launch {
            settingsRepository.setBackgroundMode(mode)
            val allowed = mode.allowedRefreshIntervals()
            if (uiState.value.refreshIntervalMinutes !in allowed) {
                settingsRepository.setRefreshIntervalMinutes(allowed.first())
            }
        }
    }

    fun onAlarmSettingsChange(alarms: AlarmSettings) {
        viewModelScope.launch { settingsRepository.setAlarmSettings(alarms) }
    }

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
