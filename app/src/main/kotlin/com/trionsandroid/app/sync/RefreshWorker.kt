package com.trionsandroid.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trionsandroid.app.data.alarm.AlarmCheckRunner
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** The "battery friendly" background mode's periodic unit of work — see BackgroundSyncScheduler. */
@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val nightscoutRepository: NightscoutRepository,
    private val alarmCheckRunner: AlarmCheckRunner,
    private val diagnosticLogger: DiagnosticLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        diagnosticLogger.log(TAG, "RefreshWorker running")
        val refreshResult = nightscoutRepository.refresh()
        refreshResult.onFailure { diagnosticLogger.logError(TAG, "Background refresh failed", it) }

        runCatching { alarmCheckRunner.checkAndNotify() }
            .onFailure { diagnosticLogger.logError(TAG, "Alarm check failed", it) }

        // Retry (with WorkManager's backoff) on a transient failure; the periodic schedule keeps
        // running regardless, this just gets a sooner extra attempt in between.
        return if (refreshResult.isSuccess) Result.success() else Result.retry()
    }

    private companion object {
        const val TAG = "RefreshWorker"
    }
}
