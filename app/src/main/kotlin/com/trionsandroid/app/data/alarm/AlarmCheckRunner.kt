package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.device.PhoneBatteryReader
import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.nightscout.formatTimeRemaining
import com.trionsandroid.app.data.nightscout.sensorTimeRemaining
import com.trionsandroid.app.data.nightscout.siteTimeRemaining
import com.trionsandroid.app.data.notification.AlarmNotifier
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Run after each refresh (by RefreshWorker and RefreshForegroundService): checks the latest
 * reading against the alarm settings and notifies when the alarm zone changes, and separately
 * evaluates the Additional Alarms (see [checkSupplementalAlarms]).
 */
class AlarmCheckRunner @Inject constructor(
    private val nightscoutRepository: NightscoutRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmStateStore: AlarmStateStore,
    private val alarmNotifier: AlarmNotifier,
    private val phoneBatteryReader: PhoneBatteryReader,
    private val diagnosticLogger: DiagnosticLogger,
) {
    suspend fun checkAndNotify() {
        val settings = settingsRepository.settings.first()
        if (!settings.alarms.alarmsEnabled) return

        // Independent of the glucose-zone logic below (which returns early in several places), so
        // it runs on every check regardless of whether glucose data is fresh or the zone changed.
        runCatching { checkSupplementalAlarms(settings.alarms) }
            .onFailure { diagnosticLogger.logError(TAG, "Supplemental alarm check failed", it) }

        val nowMillis = System.currentTimeMillis()
        // Look further back so No data can say how long it has been and show the last value.
        val sinceMillis = nowMillis - TimeUnit.HOURS.toMillis(LATEST_LOOKBACK_HOURS)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
        val latestAgeMillis = latest?.let { nowMillis - it.timestamp.toEpochMilli() } ?: Long.MAX_VALUE

        val noData = settings.alarms.noDataEnabled &&
            latestAgeMillis > TimeUnit.MINUTES.toMillis(settings.alarms.noDataMinutes.toLong())
        // Without the No data alarm, a stale reading is ignored.
        if (!noData && latestAgeMillis > TimeUnit.MINUTES.toMillis(RECENT_READING_WINDOW_MINUTES)) return

        var zone = if (noData) AlarmZone.NO_DATA else evaluateAlarmZone(latest!!.mgDl, settings.alarms)
        val lastZone = alarmStateStore.getLastZone()

        if (zone == AlarmZone.NORMAL && settings.alarms.predictedHighEnabled && latest != null) {
            val predictedSince = nowMillis - PREDICTED_HIGH_WINDOW_MILLIS - TimeUnit.MINUTES.toMillis(10)
            val readings = nightscoutRepository.observeGlucoseEntries(predictedSince).first()
            val treatments = nightscoutRepository.observeTreatments(predictedSince).first()
            if (isPredictedHigh(readings, treatments, settings.alarms, nowMillis)) {
                // Cooldown so a flickering predicate doesn't re-alert every check.
                val coolingDown = lastZone != AlarmZone.PREDICTED_HIGH &&
                    nowMillis - alarmStateStore.getLastPredictedHighNotifiedAtMillis() < PREDICTED_HIGH_COOLDOWN_MILLIS
                if (!coolingDown) zone = AlarmZone.PREDICTED_HIGH
            }
        }

        if (zone != lastZone) {
            // A new zone always notifies and resets acknowledgement, including escalations.
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

        // Same zone as last check: only a repeat of an unacknowledged alarm is left to do. Repeats can
        // only happen when a check runs (15 minutes apart in battery-friendly mode).
        if (zone == AlarmZone.NORMAL) return
        if (!settings.alarms.requireAcknowledgement || !settings.alarms.repeatIfNotAcknowledged) return
        if (alarmStateStore.isAcknowledged()) return

        val now = System.currentTimeMillis()
        if (now - alarmStateStore.getLastNotifiedAtMillis() < REPEAT_INTERVAL_MILLIS) return

        diagnosticLogger.log(TAG, "Repeating unacknowledged $zone alarm (${latest?.mgDl} mg/dL)")
        alarmStateStore.setLastNotifiedAtMillis(now)
        alarmNotifier.notify(zone, latest, settings.glucoseUnit, settings.alarms)
    }

    /**
     * The Additional Alarms: IOB, COB, reservoir, sensor/pump change, not looping and low phone
     * battery. Unlike the glucose zone above, these are independent of each other — any number can
     * be active at once — so each is tracked and notified separately (see [evaluateSupplemental]).
     */
    private suspend fun checkSupplementalAlarms(alarms: AlarmSettings) {
        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis)

        val latestStatus = nightscoutRepository.observeDeviceStatus(nowMillis - TimeUnit.HOURS.toMillis(2))
            .first()
            .maxByOrNull { it.timestamp.toEpochMilli() }
        val statusFresh = latestStatus != null &&
            nowMillis - latestStatus.timestamp.toEpochMilli() <= TimeUnit.MINUTES.toMillis(DEVICE_STATUS_FRESHNESS_MINUTES)

        evaluateSupplemental(
            kind = SupplementalAlarmKind.IOB_HIGH,
            alarms = alarms,
            isActive = alarms.iobAlarmEnabled && statusFresh &&
                (latestStatus?.iobUnits ?: Double.NEGATIVE_INFINITY) >= alarms.iobThresholdUnits,
        ) { "${oneDecimal(latestStatus?.iobUnits)} U IOB (threshold ${oneDecimal(alarms.iobThresholdUnits)} U)" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.COB_HIGH,
            alarms = alarms,
            isActive = alarms.cobAlarmEnabled && statusFresh &&
                (latestStatus?.cobGrams ?: Double.NEGATIVE_INFINITY) >= alarms.cobThresholdGrams,
        ) { "${wholeNumber(latestStatus?.cobGrams)} g COB (threshold ${wholeNumber(alarms.cobThresholdGrams)} g)" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.RESERVOIR_LOW,
            alarms = alarms,
            isActive = alarms.reservoirAlarmEnabled && statusFresh &&
                (latestStatus?.reservoirUnits ?: Double.POSITIVE_INFINITY) <= alarms.reservoirThresholdUnits,
        ) { "${oneDecimal(latestStatus?.reservoirUnits)} U left (threshold ${oneDecimal(alarms.reservoirThresholdUnits)} U)" }

        // Site (pump) and sensor change use the general treatments cache directly, which the sync
        // layer refreshes every cycle regardless of essential/full mode (see NightscoutRepositoryImpl).
        val treatments = nightscoutRepository.observeTreatments(nowMillis - TimeUnit.DAYS.toMillis(LIFECYCLE_LOOKBACK_DAYS)).first()
        val sensorRemaining = sensorTimeRemaining(treatments, now)
        val siteRemaining = siteTimeRemaining(treatments, now)

        evaluateSupplemental(
            kind = SupplementalAlarmKind.SENSOR_CHANGE_DUE,
            alarms = alarms,
            isActive = alarms.sensorChangeAlarmEnabled && sensorRemaining != null &&
                sensorRemaining.toMinutes() <= TimeUnit.HOURS.toMinutes(alarms.sensorChangeHoursThreshold.toLong()),
        // isActive guarantees non-null by the time each message() below runs.
        ) { "Sensor time left: ${formatTimeRemaining(sensorRemaining!!)}" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.PUMP_CHANGE_DUE,
            alarms = alarms,
            isActive = alarms.pumpChangeAlarmEnabled && siteRemaining != null &&
                siteRemaining.toMinutes() <= TimeUnit.HOURS.toMinutes(alarms.pumpChangeHoursThreshold.toLong()),
        ) { "Pump site time left: ${formatTimeRemaining(siteRemaining!!)}" }

        val lastLoopAt = nightscoutRepository.mostRecentConfirmedLoopAt()
        val minutesSinceLoop = lastLoopAt?.let { TimeUnit.MILLISECONDS.toMinutes(nowMillis - it.toEpochMilli()) }
        evaluateSupplemental(
            kind = SupplementalAlarmKind.NOT_LOOPING,
            alarms = alarms,
            isActive = alarms.notLoopingAlarmEnabled &&
                (minutesSinceLoop == null || minutesSinceLoop >= alarms.notLoopingMinutes),
        ) {
            if (minutesSinceLoop == null) "No confirmed loop yet" else "No confirmed loop for $minutesSinceLoop min"
        }

        val batteryPercent = phoneBatteryReader.currentLevelPercent()
        evaluateSupplemental(
            kind = SupplementalAlarmKind.LOW_PHONE_BATTERY,
            alarms = alarms,
            isActive = alarms.lowPhoneBatteryAlarmEnabled && batteryPercent != null &&
                batteryPercent <= alarms.lowPhoneBatteryPercent,
        ) { "Phone battery at $batteryPercent% (threshold ${alarms.lowPhoneBatteryPercent}%)" }
    }

    /**
     * Notifies on the false-to-true edge, clears silently on the true-to-false edge, and (with
     * [AlarmSettings.requireAcknowledgement] and [AlarmSettings.repeatIfNotAcknowledged]) repeats
     * while still active and unacknowledged — the same rules [checkAndNotify] applies to the
     * glucose zone, but tracked per [kind] since these can overlap each other and the glucose zone.
     */
    private suspend fun evaluateSupplemental(
        kind: SupplementalAlarmKind,
        alarms: AlarmSettings,
        isActive: Boolean,
        message: () -> String,
    ) {
        val wasActive = alarmStateStore.isSupplementalActive(kind)
        if (isActive) {
            if (!wasActive) {
                alarmStateStore.setSupplementalActive(kind, true)
                alarmStateStore.setSupplementalAcknowledged(kind, false)
                alarmStateStore.setSupplementalLastNotifiedAtMillis(kind, System.currentTimeMillis())
                diagnosticLogger.log(TAG, "Supplemental alarm started: ${kind.displayTitle}")
                alarmNotifier.notifySupplemental(kind, message(), alarms)
                return
            }
            if (!alarms.requireAcknowledgement || !alarms.repeatIfNotAcknowledged) return
            if (alarmStateStore.isSupplementalAcknowledged(kind)) return
            val now = System.currentTimeMillis()
            if (now - alarmStateStore.getSupplementalLastNotifiedAtMillis(kind) < REPEAT_INTERVAL_MILLIS) return
            diagnosticLogger.log(TAG, "Repeating unacknowledged supplemental alarm: ${kind.displayTitle}")
            alarmStateStore.setSupplementalLastNotifiedAtMillis(kind, now)
            alarmNotifier.notifySupplemental(kind, message(), alarms)
        } else if (wasActive) {
            alarmStateStore.setSupplementalActive(kind, false)
            alarmNotifier.cancelSupplemental(kind)
        }
    }

    private fun oneDecimal(value: Double?): String =
        value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "--"

    private fun wholeNumber(value: Double?): String = value?.roundToInt()?.toString() ?: "--"

    private companion object {
        const val TAG = "AlarmCheckRunner"
        const val RECENT_READING_WINDOW_MINUTES = 30L
        const val LATEST_LOOKBACK_HOURS = 6L
        const val DEVICE_STATUS_FRESHNESS_MINUTES = 30L
        // Long enough to catch an overdue site/sensor change; matches the sync layer's own lookback.
        const val LIFECYCLE_LOOKBACK_DAYS = 30L
        val PREDICTED_HIGH_COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(60)
        val REPEAT_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5)
    }
}
