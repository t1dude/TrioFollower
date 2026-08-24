package com.trionsandroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.InsulinProfile
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

// How far back each refresh actively re-fetches from Nightscout.
private const val REFRESH_LOOKBACK_HOURS = 24

// How far back the UI observes from the local cache — matches Room's retention window, so the
// chart can scroll back through whatever history has accumulated across refreshes over time,
// not just what the most recent refresh pulled.
private const val OBSERVE_WINDOW_HOURS = 24 * 7

private data class HomeDataState(
    val readings: List<GlucoseReading>,
    val treatments: List<Treatment>,
    val settings: UserSettings,
    val insulinProfile: InsulinProfile?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(OBSERVE_WINDOW_HOURS.toLong())

    // combine() only has typed overloads up to 5 flows; nesting keeps everything typed instead
    // of falling back to the untyped vararg/Array<*> overload.
    private val dataState = combine(
        nightscoutRepository.observeGlucoseEntries(sinceMillis),
        nightscoutRepository.observeTreatments(sinceMillis),
        settingsRepository.settings,
        nightscoutRepository.observeInsulinProfile(),
    ) { readings, treatments, settings, insulinProfile ->
        HomeDataState(readings, treatments, settings, insulinProfile)
    }

    val uiState: StateFlow<HomeUiState> = combine(dataState, isLoading, errorMessage) { data, loading, error ->
        HomeUiState(
            isLoading = loading,
            errorMessage = error,
            glucoseUnit = data.settings.glucoseUnit,
            alarms = data.settings.alarms,
            readings = data.readings.sortedByDescending { it.timestamp },
            treatments = data.treatments.sortedByDescending { it.timestamp },
            insulinProfile = data.insulinProfile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

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
