package com.trionsandroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.nightscout.DeviceStatusPoint
import com.trionsandroid.app.data.nightscout.ConnectionEvents
import com.trionsandroid.app.data.nightscout.Forecast
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.update.UpdateChecker
import com.trionsandroid.app.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// How far back each refresh re-fetches.
private const val REFRESH_LOOKBACK_HOURS = 24

// How far back the UI reads from the cache (Room's retention window).
private const val OBSERVE_WINDOW_HOURS = 24 * 30

private data class HomeDataState(
    val readings: List<GlucoseReading>,
    val treatments: List<Treatment>,
    val settings: UserSettings,
    val insulinProfile: InsulinProfile?,
    val deviceStatusPoints: List<DeviceStatusPoint>,
    val forecast: Forecast? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    settingsRepository: SettingsRepository,
    connectionEvents: ConnectionEvents,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val refreshCount = MutableStateFlow(0)
    private val forceScroll = MutableStateFlow(true)
    private val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(OBSERVE_WINDOW_HOURS.toLong())

    // combine() is typed only up to 5 flows, so nest.
    private val dataState = combine(
        nightscoutRepository.observeGlucoseEntries(sinceMillis),
        nightscoutRepository.observeTreatments(sinceMillis),
        settingsRepository.settings,
        nightscoutRepository.observeInsulinProfile(),
        nightscoutRepository.observeDeviceStatus(sinceMillis),
    ) { readings, treatments, settings, insulinProfile, deviceStatusPoints ->
        HomeDataState(readings, treatments, settings, insulinProfile, deviceStatusPoints)
    }.combine(nightscoutRepository.observeLatestForecast()) { data, forecast -> data.copy(forecast = forecast) }

    val uiState: StateFlow<HomeUiState> = combine(dataState, isLoading, errorMessage, refreshCount, forceScroll) { data, loading, error, refreshes, forced ->
        HomeUiState(
            isLoading = loading,
            errorMessage = error,
            refreshCount = refreshes,
            forceScrollToLatest = forced,
            refreshIntervalMinutes = data.settings.refreshIntervalMinutes,
            showNowLine = data.settings.showNowLine,
            bolusDisplayThreshold = data.settings.bolusDisplayThreshold,
            glucoseColorScheme = data.settings.glucoseColorScheme,
            homeStatsFace = data.settings.homeStatsFace,
            forecast = data.forecast,
            forecastDisplay = data.settings.forecastDisplay,
            glucoseUnit = data.settings.glucoseUnit,
            timeFormat = data.settings.timeFormat,
            alarms = data.settings.alarms,
            // The DAO returns these oldest first, so newest first is just the reverse.
            readings = data.readings.asReversed(),
            treatments = data.treatments.asReversed(),
            insulinProfile = data.insulinProfile,
            deviceStatusPoints = data.deviceStatusPoints,
        )
    }.combine(updateChecker.availableUpdate) { state, update -> state.copy(update = update) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { updateChecker.checkIfDue() }
        // After a verified connection, clear the stale error and sync right away.
        viewModelScope.launch {
            connectionEvents.established.collect {
                errorMessage.value = null
                refresh(userInitiated = true)
            }
        }
    }

    /** [userInitiated] is false for automatic ticks, which shouldn't pull the chart back from history. */
    fun dismissUpdate(version: String) {
        viewModelScope.launch { updateChecker.dismiss(version) }
    }

    suspend fun reasoningFor(reading: GlucoseReading) =
        nightscoutRepository.getReasoningForReading(reading.timestamp)

    fun refresh(userInitiated: Boolean = true) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            nightscoutRepository.refresh(lookbackHours = REFRESH_LOOKBACK_HOURS)
                .onFailure { errorMessage.value = it.message ?: "Couldn't refresh from Nightscout" }
            isLoading.value = false
            forceScroll.value = userInitiated
            refreshCount.value++
        }
    }
}
