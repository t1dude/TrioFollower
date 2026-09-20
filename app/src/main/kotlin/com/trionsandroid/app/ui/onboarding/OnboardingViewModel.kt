package com.trionsandroid.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trionsandroid.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** True for new users only: welcome not completed and no URL set. Starts false to avoid a flash for existing users. */
    val showWelcome: StateFlow<Boolean> = settingsRepository.settings
        .map { !it.welcomeCompleted && it.nightscoutUrl.isBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun completeWelcome() {
        viewModelScope.launch { settingsRepository.setWelcomeCompleted(true) }
    }
}
