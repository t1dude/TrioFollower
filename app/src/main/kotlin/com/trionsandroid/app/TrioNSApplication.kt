package com.trionsandroid.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.sync.BackgroundSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TrioNSApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backgroundSyncScheduler: BackgroundSyncScheduler
    @Inject lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Re-applied whenever background mode or interval changes, and once at startup.
        applicationScope.launch {
            settingsRepository.settings
                .distinctUntilChangedBy { it.backgroundMode to it.refreshIntervalMinutes }
                .collect { backgroundSyncScheduler.apply(it) }
        }
    }
}
