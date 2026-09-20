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

        val nowMillis = System.currentTimeMillis()
        // Looks further back than RECENT_READING_WINDOW_MINUTES so that, when the newest reading
        // is stale, the No data alarm can still say how long it's been (and show the last value).
        val sinceMillis = nowMillis - TimeUnit.HOURS.toMillis(LATEST_LOOKBACK_HOURS)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
        val latestAgeMillis = latest?.let { nowMillis - it.timestamp.toEpochMilli() } ?: Long.MAX_VALUE

        val noData = settings.alarms.noDataEnabled &&
            latestAgeMillis > TimeUnit.MINUTES.toMillis(settings.alarms.noDataMinutes.toLong())
        // Without the No data alarm, a stale reading is simply ignored, as before.
        if (!noData && latestAgeMillis > TimeUnit.MINUTES.toMillis(RECENT_READING_WINDOW_MINUTES)) return

        var zone = if (noData) AlarmZone.NO_DATA else evaluateAlarmZone(latest!!.mgDl, settings.alarms)
        val lastZone = alarmStateStore.getLastZone()

        if (zone == AlarmZone.NORMAL && settings.alarms.predictedHighEnabled && latest != null) {
            val predictedSince = nowMillis - PREDICTED_HIGH_WINDOW_MILLIS - TimeUnit.MINUTES.toMillis(10)
            val readings = nightscoutRepository.observeGlucoseEntries(predictedSince).first()
            val treatments = nightscoutRepository.observeTreatments(predictedSince).first()
            if (isPredictedHigh(readings, treatments, settings.alarms, nowMillis)) {
                // Cooldown against flapping in and out of the predicate re-alerting every check:
                // a fresh (not continuing) predicted-high alarm waits an hour after the last one.
                val coolingDown = lastZone != AlarmZone.PREDICTED_HIGH &&
                    nowMillis - alarmStateStore.getLastPredictedHighNotifiedAtMillis() < PREDICTED_HIGH_COOLDOWN_MILLIS
                if (!coolingDown) zone = AlarmZone.PREDICTED_HIGH
            }
        }

        if (zone != lastZone) {
            // A fresh zone (including escalating from e.g. low to urgent-low) always notifies
            // and resets acknowledgement, even if the previous zone's alarm was never acked.
            alarmStateStore.setLastZone(zone)
            alarmStateStore.setAcknowledged(zone == AlarmZone.NORMAL)
            diagnosticLogger.log(TAG, "Alarm zone changed: $lastZone -> $zone (${latest?.mgDl} mg/dL)")
            if (zone != AlarmZone.NORMAL) {
                alarmStateStore.setLastNotifiedAtMillis(System.currentTimeMillis())
                if (zone == AlarmZone.PREDICTED_HIGH) {
                    alarmStateStore.setLastPredictedHighNotifiedAtMillis(System.currentTimeMillis())
                }
                alarmNotifier.notify(zone, latest, settings.glucoseUnit, settings.alarms)
            }
            return
        }

        // Same zone as last check — the only reason to act again is repeating an unacknowledged
        // alarm. Repeats can't fire more often than this is actually called, so with e.g. the
        // WorkManager "battery friendly" mode's 15-minute floor, the 5-minute repeat interval
        // below is really "next time we check after 5 minutes have passed."
        if (zone == AlarmZone.NORMAL) return
        if (!settings.alarms.requireAcknowledgement || !settings.alarms.repeatIfNotAcknowledged) return
        if (alarmStateStore.isAcknowledged()) return

        val now = System.currentTimeMillis()
        if (now - alarmStateStore.getLastNotifiedAtMillis() < REPEAT_INTERVAL_MILLIS) return

        diagnosticLogger.log(TAG, "Repeating unacknowledged $zone alarm (${latest?.mgDl} mg/dL)")
        alarmStateStore.setLastNotifiedAtMillis(now)
        alarmNotifier.notify(zone, latest, settings.glucoseUnit, settings.alarms)
    }

    private companion object {
        const val TAG = "AlarmCheckRunner"
        const val RECENT_READING_WINDOW_MINUTES = 30L
        const val LATEST_LOOKBACK_HOURS = 6L
        val PREDICTED_HIGH_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(60)
        val REPEAT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
    }
}
