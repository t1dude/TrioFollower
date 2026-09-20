package com.trionsandroid.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.SettingsRepository
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

// How far back each refresh actively re-fetches from Nightscout — matches HomeViewModel's.
private const val REFRESH_LOOKBACK_HOURS = 24

// How far back the UI observes from the local cache — matches Room's retention window (see
// NightscoutRepositoryImpl.RETENTION_HOURS) so History can scroll back through everything
// actually cached, not just what the most recent refresh pulled.
private const val OBSERVE_WINDOW_HOURS = 24 * 30

private data class HistoryDataState(
    val readings: List<GlucoseReading>,
    val treatments: List<Treatment>,
    val settings: UserSettings,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(OBSERVE_WINDOW_HOURS.toLong())

    private val dataState = combine(
        nightscoutRepository.observeGlucoseEntries(sinceMillis),
        nightscoutRepository.observeTreatments(sinceMillis),
        settingsRepository.settings,
    ) { readings, treatments, settings ->
        HistoryDataState(readings, treatments, settings)
    }

    val uiState: StateFlow<HistoryUiState> = combine(dataState, isLoading, errorMessage) { data, loading, error ->
        HistoryUiState(
            isLoading = loading,
            errorMessage = error,
            glucoseUnit = data.settings.glucoseUnit,
            timeFormat = data.settings.timeFormat,
            alarms = data.settings.alarms,
            glucoseColorScheme = data.settings.glucoseColorScheme,
            readings = data.readings.sortedByDescending { it.timestamp },
            treatments = data.treatments.sortedByDescending { it.timestamp },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        refresh()
    }

    suspend fun reasoningFor(reading: GlucoseReading) =
        nightscoutRepository.getReasoningForReading(reading.timestamp)

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            nightscoutRepository.refresh(lookbackHours = REFRESH_LOOKBACK_HOURS)
                .onFailure { errorMessage.value = it.message ?: "Couldn't refresh from Nightscout" }
            isLoading.value = false
        }
    }
}
