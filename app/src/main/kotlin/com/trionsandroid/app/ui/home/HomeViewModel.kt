package com.trionsandroid.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val LOOKBACK_HOURS = 24

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val sinceMillis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(LOOKBACK_HOURS.toLong())

    val uiState: StateFlow<HomeUiState> = combine(
        nightscoutRepository.observeGlucoseEntries(sinceMillis),
        nightscoutRepository.observeTreatments(sinceMillis),
        settingsRepository.settings,
        isLoading,
        errorMessage,
    ) { readings, treatments, settings, loading, error ->
        HomeUiState(
            isLoading = loading,
            errorMessage = error,
            glucoseUnit = settings.glucoseUnit,
            alarms = settings.alarms,
            readings = readings.sortedByDescending { it.timestamp },
            treatments = treatments.sortedByDescending { it.timestamp },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null
            nightscoutRepository.refresh(lookbackHours = LOOKBACK_HOURS)
                .onFailure { errorMessage.value = it.message ?: "Couldn't refresh from Nightscout" }
            isLoading.value = false
        }
    }
}
