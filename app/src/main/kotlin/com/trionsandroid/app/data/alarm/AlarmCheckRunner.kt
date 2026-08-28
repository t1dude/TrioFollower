package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.notification.AlarmNotifier
import com.trionsandroid.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Shared by RefreshWorker and RefreshForegroundService: after a refresh, checks the latest
 * glucose reading against the user's alarm thresholds and fires a notification on a zone change.
 * Assumes the caller already ran NightscoutRepository.refresh() this cycle.
 */
class AlarmCheckRunner @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmStateStore: AlarmStateStore,
    private val alarmNotifier: AlarmNotifier,
    private val diagnosticLogger: DiagnosticLogger,
) {
    suspend fun checkAndNotify() {
        val settings = settingsRepository.settings.first()
        if (!settings.alarms.alarmsEnabled) return

        val sinceMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(RECENT_READING_WINDOW_MINUTES)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
            ?: return

        val zone = evaluateAlarmZone(latest.mgDl, settings.alarms)
        val lastZone = alarmStateStore.getLastZone()
        if (zone == lastZone) return

        alarmStateStore.setLastZone(zone)
        diagnosticLogger.log(TAG, "Alarm zone changed: $lastZone -> $zone (${latest.mgDl} mg/dL)")
        if (zone != AlarmZone.NORMAL) {
            alarmNotifier.notify(zone, latest, settings.glucoseUnit, settings.alarms)
        }
    }

    private companion object {
        const val TAG = "AlarmCheckRunner"
        const val RECENT_READING_WINDOW_MINUTES = 30L
    }
}
