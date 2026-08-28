package com.trionsandroid.app.sync

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.settings.BackgroundMode
import com.trionsandroid.app.data.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the user's chosen background mode/interval: exactly one of WorkManager's periodic
 * work or the foreground service is ever active, and switching modes tears down the other one.
 * Called once at app startup and again on every relevant settings change — see
 * TrioNSApplication.onCreate().
 */
@Singleton
class BackgroundSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticLogger: DiagnosticLogger,
) {
    fun apply(settings: UserSettings) {
        when (settings.backgroundMode) {
            BackgroundMode.WORK_MANAGER -> {
                context.stopService(Intent(context, RefreshForegroundService::class.java))
                // WorkManager enforces a 15-minute floor on periodic work regardless of what we
                // request; BackgroundMode.allowedRefreshIntervals() already only offers >=15 for
                // this mode, but coerce defensively in case that ever drifts.
                val intervalMinutes = settings.refreshIntervalMinutes.coerceAtLeast(15)
                val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
                diagnosticLogger.log(TAG, "Scheduled WorkManager sync every ${intervalMinutes}m")
            }

            BackgroundMode.FOREGROUND_SERVICE -> {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                val intent = Intent(context, RefreshForegroundService::class.java)
                    .putExtra(RefreshForegroundService.EXTRA_INTERVAL_MINUTES, settings.refreshIntervalMinutes)
                try {
                    ContextCompat.startForegroundService(context, intent)
                    diagnosticLogger.log(TAG, "Started foreground sync service every ${settings.refreshIntervalMinutes}m")
                } catch (e: IllegalStateException) {
                    // Android 12+ can refuse a foreground-service start when this code runs
                    // outside a user-visible context (e.g. the OS waking our process for some
                    // unrelated reason rather than the user opening the app). Not fatal — the
                    // service starts normally next time this runs from an eligible context
                    // (typically just reopening the app).
                    diagnosticLogger.logError(TAG, "Foreground service start was refused by the OS", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "BackgroundSyncScheduler"
        const val UNIQUE_WORK_NAME = "trio_refresh_sync"
    }
}
