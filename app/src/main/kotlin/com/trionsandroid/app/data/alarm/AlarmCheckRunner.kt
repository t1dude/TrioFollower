package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.logging.DiagnosticLogger
import com.trionsandroid.app.data.nightscout.NightscoutRepository
import com.trionsandroid.app.data.nightscout.formatTimeRemaining
import com.trionsandroid.app.data.nightscout.sensorTimeRemaining
import com.trionsandroid.app.data.nightscout.siteTimeRemaining
import com.trionsandroid.app.data.notification.AlarmNotifier
import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.SettingsRepository
import com.trionsandroid.app.data.settings.toMinuteOfDay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Run after each refresh (by RefreshWorker and RefreshForegroundService): checks the latest
 * reading against the alarm settings and notifies when the alarm zone changes, and separately
 * evaluates the Additional Alarms (see [checkSupplementalAlarms]). Every alarm now carries its own
 * [AlarmBehavior] (day/night, sound, vibration, acknowledgement), gated by [isAllowedNow].
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
        val alarms = settings.alarms
        val nowMinuteOfDay = LocalTime.now().toMinuteOfDay()

        // Independent of the glucose-zone logic below (which returns early in several places), so
        // it runs on every check regardless of whether glucose data is fresh or the zone changed.
        runCatching { checkSupplementalAlarms(alarms, nowMinuteOfDay) }
            .onFailure { diagnosticLogger.logError(TAG, "Supplemental alarm check failed", it) }

        val nowMillis = System.currentTimeMillis()
        // Look further back so No data can say how long it has been and show the last value.
        val sinceMillis = nowMillis - TimeUnit.HOURS.toMillis(LATEST_LOOKBACK_HOURS)
        val latest = nightscoutRepository.observeGlucoseEntries(sinceMillis).first().maxByOrNull { it.timestamp }
        val latestAgeMillis = latest?.let { nowMillis - it.timestamp.toEpochMilli() } ?: Long.MAX_VALUE

        val noData = alarms.noData.enabled && isAllowedNow(alarms.noData.behavior, alarms.dayNightWindow, nowMinuteOfDay) &&
            latestAgeMillis > TimeUnit.MINUTES.toMillis(alarms.noData.minutes.toLong())
        // Without an allowed No data alarm, a stale reading is ignored.
        if (!noData && latestAgeMillis > TimeUnit.MINUTES.toMillis(RECENT_READING_WINDOW_MINUTES)) return

        var zone = if (noData) AlarmZone.NO_DATA else evaluateAlarmZone(latest!!.mgDl, alarms, nowMinuteOfDay)
        val lastZone = alarmStateStore.getLastZone()

        if (zone == AlarmZone.NORMAL && alarms.predictedHigh.enabled &&
            isAllowedNow(alarms.predictedHigh.behavior, alarms.dayNightWindow, nowMinuteOfDay) && latest != null
        ) {
            val predictedSince = nowMillis - PREDICTED_HIGH_WINDOW_MILLIS - TimeUnit.MINUTES.toMillis(10)
            val readings = nightscoutRepository.observeGlucoseEntries(predictedSince).first()
            val treatments = nightscoutRepository.observeTreatments(predictedSince).first()
            if (isPredictedHigh(readings, treatments, alarms, nowMillis)) {
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
                alarmNotifier.notify(zone, latest, settings.glucoseUnit, alarms.behaviorFor(zone))
            }
            return
        }

        // Same zone as last check: only a repeat of an unacknowledged alarm is left to do. Repeats can
        // only happen when a check runs (15 minutes apart in battery-friendly mode).
        if (zone == AlarmZone.NORMAL) return
        val behavior = alarms.behaviorFor(zone)
        if (!behavior.requireAcknowledgement || !behavior.repeatIfNotAcknowledged) return
        if (alarmStateStore.isAcknowledged()) return

        val now = System.currentTimeMillis()
        if (now - alarmStateStore.getLastNotifiedAtMillis() < REPEAT_INTERVAL_MILLIS) return

        diagnosticLogger.log(TAG, "Repeating unacknowledged $zone alarm (${latest?.mgDl} mg/dL)")
        alarmStateStore.setLastNotifiedAtMillis(now)
        alarmNotifier.notify(zone, latest, settings.glucoseUnit, behavior)
    }

    /**
     * The Additional Alarms: IOB, COB, reservoir, sensor/pump change, not looping, low phone
     * battery and the random alarm. Unlike the glucose zone above, these are independent of each
     * other — any number can be active at once — so each is tracked and notified separately (see
     * [evaluateSupplemental] and, for the random alarm's different event-based shape, [checkRandomAlarm]).
     */
    private suspend fun checkSupplementalAlarms(alarms: AlarmSettings, nowMinuteOfDay: Int) {
        val nowMillis = System.currentTimeMillis()
        val now = Instant.ofEpochMilli(nowMillis)
        val window = alarms.dayNightWindow

        val latestStatus = nightscoutRepository.observeDeviceStatus(nowMillis - TimeUnit.HOURS.toMillis(2))
            .first()
            .maxByOrNull { it.timestamp.toEpochMilli() }
        val statusFresh = latestStatus != null &&
            nowMillis - latestStatus.timestamp.toEpochMilli() <= TimeUnit.MINUTES.toMillis(DEVICE_STATUS_FRESHNESS_MINUTES)

        evaluateSupplemental(
            kind = SupplementalAlarmKind.IOB_HIGH,
            behavior = alarms.iob.behavior,
            isActive = alarms.iob.enabled && isAllowedNow(alarms.iob.behavior, window, nowMinuteOfDay) && statusFresh &&
                (latestStatus?.iobUnits ?: Double.NEGATIVE_INFINITY) >= alarms.iob.thresholdUnits,
        ) { "${oneDecimal(latestStatus?.iobUnits)} U IOB (threshold ${oneDecimal(alarms.iob.thresholdUnits)} U)" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.COB_HIGH,
            behavior = alarms.cob.behavior,
            isActive = alarms.cob.enabled && isAllowedNow(alarms.cob.behavior, window, nowMinuteOfDay) && statusFresh &&
                (latestStatus?.cobGrams ?: Double.NEGATIVE_INFINITY) >= alarms.cob.thresholdGrams,
        ) { "${wholeNumber(latestStatus?.cobGrams)} g COB (threshold ${wholeNumber(alarms.cob.thresholdGrams)} g)" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.RESERVOIR_LOW,
            behavior = alarms.reservoir.behavior,
            isActive = alarms.reservoir.enabled && isAllowedNow(alarms.reservoir.behavior, window, nowMinuteOfDay) &&
                statusFresh && (latestStatus?.reservoirUnits ?: Double.POSITIVE_INFINITY) <= alarms.reservoir.thresholdUnits,
        ) { "${oneDecimal(latestStatus?.reservoirUnits)} U left (threshold ${oneDecimal(alarms.reservoir.thresholdUnits)} U)" }

        // Site (pump) and sensor change use the general treatments cache directly, which the sync
        // layer refreshes every cycle regardless of essential/full mode (see NightscoutRepositoryImpl).
        val treatments = nightscoutRepository.observeTreatments(nowMillis - TimeUnit.DAYS.toMillis(LIFECYCLE_LOOKBACK_DAYS)).first()
        val sensorRemaining = sensorTimeRemaining(treatments, now)
        val siteRemaining = siteTimeRemaining(treatments, now)

        evaluateSupplemental(
            kind = SupplementalAlarmKind.SENSOR_CHANGE_DUE,
            behavior = alarms.sensorChange.behavior,
            isActive = alarms.sensorChange.enabled && isAllowedNow(alarms.sensorChange.behavior, window, nowMinuteOfDay) &&
                sensorRemaining != null &&
                sensorRemaining.toMinutes() <= TimeUnit.HOURS.toMinutes(alarms.sensorChange.hoursThreshold.toLong()),
        // isActive guarantees non-null by the time each message() below runs.
        ) { "Sensor time left: ${formatTimeRemaining(sensorRemaining!!)}" }

        evaluateSupplemental(
            kind = SupplementalAlarmKind.PUMP_CHANGE_DUE,
            behavior = alarms.pumpChange.behavior,
            isActive = alarms.pumpChange.enabled && isAllowedNow(alarms.pumpChange.behavior, window, nowMinuteOfDay) &&
                siteRemaining != null &&
                siteRemaining.toMinutes() <= TimeUnit.HOURS.toMinutes(alarms.pumpChange.hoursThreshold.toLong()),
        ) { "Pump site time left: ${formatTimeRemaining(siteRemaining!!)}" }

        val lastLoopAt = nightscoutRepository.mostRecentConfirmedLoopAt()
        val minutesSinceLoop = lastLoopAt?.let { TimeUnit.MILLISECONDS.toMinutes(nowMillis - it.toEpochMilli()) }
        evaluateSupplemental(
            kind = SupplementalAlarmKind.NOT_LOOPING,
            behavior = alarms.notLooping.behavior,
            isActive = alarms.notLooping.enabled && isAllowedNow(alarms.notLooping.behavior, window, nowMinuteOfDay) &&
                (minutesSinceLoop == null || minutesSinceLoop >= alarms.notLooping.minutes),
        ) {
            if (minutesSinceLoop == null) "No confirmed loop yet" else "No confirmed loop for $minutesSinceLoop min"
        }

        // The Trio (looping) phone's own battery, as it reports itself to Nightscout — not the
        // battery of the phone this app runs on.
        val uploaderBattery = latestStatus?.uploaderBatteryPercent?.roundToInt()
        evaluateSupplemental(
            kind = SupplementalAlarmKind.LOW_PHONE_BATTERY,
            behavior = alarms.lowPhoneBattery.behavior,
            isActive = alarms.lowPhoneBattery.enabled && isAllowedNow(alarms.lowPhoneBattery.behavior, window, nowMinuteOfDay) &&
                statusFresh && uploaderBattery != null && uploaderBattery <= alarms.lowPhoneBattery.percent,
        ) { "Trio phone battery at $uploaderBattery% (threshold ${alarms.lowPhoneBattery.percent}%)" }

        checkRandomAlarm(alarms, nowMinuteOfDay)
    }

    /**
     * Notifies on the false-to-true edge, clears silently on the true-to-false edge (which also
     * covers a day/night restriction newly kicking in), and (with [AlarmBehavior.requireAcknowledgement]
     * and [AlarmBehavior.repeatIfNotAcknowledged]) repeats while still active and unacknowledged —
     * the same rules [checkAndNotify] applies to the glucose zone, but tracked per [kind] since
     * these can overlap each other and the glucose zone.
     */
    private suspend fun evaluateSupplemental(
        kind: SupplementalAlarmKind,
        behavior: AlarmBehavior,
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
                alarmNotifier.notifySupplemental(kind, message(), behavior)
                return
            }
            if (!behavior.requireAcknowledgement || !behavior.repeatIfNotAcknowledged) return
            if (alarmStateStore.isSupplementalAcknowledged(kind)) return
            val now = System.currentTimeMillis()
            if (now - alarmStateStore.getSupplementalLastNotifiedAtMillis(kind) < REPEAT_INTERVAL_MILLIS) return
            diagnosticLogger.log(TAG, "Repeating unacknowledged supplemental alarm: ${kind.displayTitle}")
            alarmStateStore.setSupplementalLastNotifiedAtMillis(kind, now)
            alarmNotifier.notifySupplemental(kind, message(), behavior)
        } else if (wasActive) {
            alarmStateStore.setSupplementalActive(kind, false)
            alarmNotifier.cancelSupplemental(kind)
        }
    }

    /**
     * Fires at a few random times a day (1-4), for no reason — see RandomAlarmInfoSheet. Unlike the
     * other supplemental alarms, this isn't a condition that's true or false each check; it's a
     * one-off event, so it has its own scheduling instead of going through [evaluateSupplemental].
     */
    private suspend fun checkRandomAlarm(alarms: AlarmSettings, nowMinuteOfDay: Int) {
        if (!alarms.randomAlarm.enabled) return
        val kind = SupplementalAlarmKind.RANDOM_ALARM
        val behavior = alarms.randomAlarm.behavior
        val nowMillis = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        if (alarmStateStore.getRandomAlarmScheduleDate() != today) {
            val times = randomAlarmTimesForToday()
            alarmStateStore.setRandomAlarmScheduleDate(today)
            alarmStateStore.setRandomAlarmPendingMillis(times)
            diagnosticLogger.log(TAG, "Random alarm: ${times.size} time(s) scheduled for today")
        }

        val pending = alarmStateStore.getRandomAlarmPendingMillis()
        val due = pending.filter { it <= nowMillis }
        if (due.isNotEmpty()) {
            // One notification per check even if more than one came due at once (e.g. after a long
            // gap in background checks); the rest are simply dropped rather than bursting several.
            alarmStateStore.setRandomAlarmPendingMillis(pending - due.toSet())
            if (!isAllowedNow(behavior, alarms.dayNightWindow, nowMinuteOfDay)) {
                diagnosticLogger.log(TAG, "Random alarm was due but Day/Night settings don't allow it now; dropped")
                return
            }
            alarmStateStore.setSupplementalAcknowledged(kind, false)
            alarmStateStore.setSupplementalLastNotifiedAtMillis(kind, nowMillis)
            diagnosticLogger.log(TAG, "Random alarm firing")
            alarmNotifier.notifySupplemental(kind, RANDOM_ALARM_MESSAGES.random(), behavior)
            return
        }

        // No new fire this cycle: repeat only an unacknowledged one, same as the other alarms.
        // isSupplementalAcknowledged defaults to true, so this is a no-op before the first ever fire.
        if (!behavior.requireAcknowledgement || !behavior.repeatIfNotAcknowledged) return
        if (alarmStateStore.isSupplementalAcknowledged(kind)) return
        if (nowMillis - alarmStateStore.getSupplementalLastNotifiedAtMillis(kind) < REPEAT_INTERVAL_MILLIS) return
        diagnosticLogger.log(TAG, "Repeating unacknowledged random alarm")
        alarmStateStore.setSupplementalLastNotifiedAtMillis(kind, nowMillis)
        alarmNotifier.notifySupplemental(kind, RANDOM_ALARM_MESSAGES.random(), behavior)
    }

    /** 1 to 4 random instants, uniformly spread across today (the device's local day). */
    private fun randomAlarmTimesForToday(): List<Long> {
        val dayStartMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayLengthMillis = TimeUnit.DAYS.toMillis(1)
        val count = Random.nextInt(1, MAX_RANDOM_ALARMS_PER_DAY + 1)
        return List(count) { dayStartMillis + Random.nextLong(dayLengthMillis) }.sorted()
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
        const val MAX_RANDOM_ALARMS_PER_DAY = 4
        val RANDOM_ALARM_MESSAGES = listOf(
            "Beep. No reason. Carry on.",
            "This is not a drill. Or is it?",
            "Just checking you're still paying attention.",
            "Nothing's wrong. We just missed you.",
            "A completely unnecessary alarm, exactly as requested.",
        )
    }
}
