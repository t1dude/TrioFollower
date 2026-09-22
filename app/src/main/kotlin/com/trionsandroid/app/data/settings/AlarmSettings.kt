package com.trionsandroid.app.data.settings

import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * Cross-cutting behavior every alarm sets on its own: when it's allowed to fire, and how it
 * notifies. An alarm with both [fireDay] and [fireNight] on (the default) has no time restriction
 * at all, so it is never affected by gaps or overlaps in the user's own Day/Night windows — those
 * only matter to an alarm deliberately restricted to just one of them. See AlarmScheduling.isAllowedNow.
 */
@Serializable
data class AlarmBehavior(
    val fireDay: Boolean = true,
    val fireNight: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Notifications can't be swiped away until acknowledged. */
    val requireAcknowledgement: Boolean = false,
    /** With [requireAcknowledgement], re-alert until acknowledged. */
    val repeatIfNotAcknowledged: Boolean = false,
)

/**
 * The user's Day and Night time-of-day windows, each independently settable and each allowed to
 * wrap past midnight (e.g. Night 22:00-07:00). Stored as minutes-of-day (0-1439), not [LocalTime],
 * so this needs no custom (de)serializer.
 */
@Serializable
data class DayNightWindow(
    val dayStartMinute: Int = 7 * 60,
    val dayEndMinute: Int = 22 * 60,
    val nightStartMinute: Int = 22 * 60,
    val nightEndMinute: Int = 7 * 60,
)

fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

@Serializable
data class GlucoseThresholdAlarm(
    val enabled: Boolean,
    val thresholdMgDl: Int,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class PredictedHighAlarm(
    val enabled: Boolean = false,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class NoDataAlarm(
    val enabled: Boolean = false,
    val minutes: Int = 20,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class IobAlarm(
    val enabled: Boolean = false,
    val thresholdUnits: Double = 5.0,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class CobAlarm(
    val enabled: Boolean = false,
    val thresholdGrams: Double = 30.0,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class ReservoirAlarm(
    val enabled: Boolean = false,
    val thresholdUnits: Double = 20.0,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class SensorChangeAlarm(
    val enabled: Boolean = false,
    val hoursThreshold: Int = 8,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class PumpChangeAlarm(
    val enabled: Boolean = false,
    val hoursThreshold: Int = 8,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class NotLoopingAlarm(
    val enabled: Boolean = false,
    val minutes: Int = 20,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

@Serializable
data class LowPhoneBatteryAlarm(
    val enabled: Boolean = false,
    val percent: Int = 20,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

/**
 * For when the other alarms aren't quite enough alarm fatigue on their own: fires at a few random
 * times a day (up to 4), for no reason at all. No threshold to set.
 */
@Serializable
data class RandomAlarmConfig(
    val enabled: Boolean = false,
    val behavior: AlarmBehavior = AlarmBehavior(),
)

/** The selectable "no new data" limits, in minutes. */
val NO_DATA_MINUTES_OPTIONS = listOf(20, 40, 60)

/** The selectable "not looping" limits, in minutes. */
val NOT_LOOPING_MINUTES_OPTIONS = listOf(20, 40, 60)

@Serializable
data class AlarmSettings(
    val alarmsEnabled: Boolean = true,
    val dayNightWindow: DayNightWindow = DayNightWindow(),

    // --- Glucose Alarms ---
    val urgentLow: GlucoseThresholdAlarm = GlucoseThresholdAlarm(enabled = true, thresholdMgDl = 55),
    val low: GlucoseThresholdAlarm = GlucoseThresholdAlarm(enabled = true, thresholdMgDl = 70),
    val high: GlucoseThresholdAlarm = GlucoseThresholdAlarm(enabled = true, thresholdMgDl = 180),
    val urgentHigh: GlucoseThresholdAlarm = GlucoseThresholdAlarm(enabled = true, thresholdMgDl = 250),

    // --- Additional Alarms: each is independent of the glucose zone and of the others. ---
    /** A slow, steady in-range climb toward the high threshold (see PredictedHighEvaluator). */
    val predictedHigh: PredictedHighAlarm = PredictedHighAlarm(),
    val noData: NoDataAlarm = NoDataAlarm(),
    val iob: IobAlarm = IobAlarm(),
    val cob: CobAlarm = CobAlarm(),
    val reservoir: ReservoirAlarm = ReservoirAlarm(),
    val sensorChange: SensorChangeAlarm = SensorChangeAlarm(),
    val pumpChange: PumpChangeAlarm = PumpChangeAlarm(),
    val notLooping: NotLoopingAlarm = NotLoopingAlarm(),
    /** The Trio (looping) phone's own battery, as it reports itself to Nightscout. */
    val lowPhoneBattery: LowPhoneBatteryAlarm = LowPhoneBatteryAlarm(),
    val randomAlarm: RandomAlarmConfig = RandomAlarmConfig(),
)
