package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.nightscout.isBolusEventType
import com.trionsandroid.app.data.nightscout.isSmbEventType
import com.trionsandroid.app.data.settings.AlarmSettings
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** How far back the readings/treatments the evaluator looks at reach — the caller needs to
 *  supply at least this much (plus a little slack) of both. */
val PREDICTED_HIGH_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(60)

private val PROJECTION_MINUTES = 60.0
private val MAX_GAP_MILLIS = TimeUnit.MINUTES.toMillis(15)
private val MIN_COVERAGE_MILLIS = TimeUnit.MINUTES.toMillis(55)
private val SEGMENT_MILLIS = TimeUnit.MINUTES.toMillis(15)
private val SEGMENT_MATCH_TOLERANCE_MILLIS = TimeUnit.MINUTES.toMillis(5)
private const val MIN_READINGS = 8
private const val MIN_SLOPE_MGDL_PER_HOUR = 10.0
private const val MAX_SLOPE_MGDL_PER_HOUR = 45.0
private const val MIN_SEGMENT_DELTA_MGDL = -2
private const val MAX_SEGMENT_DELTA_MGDL = 20

/**
 * "Predicted high": glucose has been in range for the past hour and climbing slowly and steadily
 * toward the high threshold — meant to catch a slow drift upward (e.g. a lazy morning) before it
 * becomes a High alarm. Deliberately does *not* fire on a fast rise (meal-like, and the High alarm
 * covers it) or when a bolus (excluding SMBs, which the loop does on its own) or carbs were
 * logged during the window.
 *
 * All of these must hold over the last 60 minutes ending at the newest reading:
 *  1. Enough data: readings span ≥55 min, ≥8 readings, no gap >15 min (also from the newest
 *     reading to [nowMillis]).
 *  2. Every reading is strictly between the low and high thresholds.
 *  3. A least-squares line through the readings has a slope of +10..+45 mg/dL per hour.
 *  4. Steady: split into four 15-minute segments, each segment's change is ≥ -2 (sensor-noise
 *     tolerance) and ≤ +20 mg/dL (no meal-like jump hiding inside a gentle average).
 *  5. Latest + slope × 60 min reaches the high threshold.
 *  6. No non-SMB bolus and no carb entry in the window.
 */
fun isPredictedHigh(
    readings: List<GlucoseReading>,
    treatments: List<Treatment>,
    alarms: AlarmSettings,
    nowMillis: Long,
): Boolean {
    val sorted = readings.sortedBy { it.timestamp }
    val latest = sorted.lastOrNull() ?: return false
    val endMillis = latest.timestamp.toEpochMilli()
    if (nowMillis - endMillis > MAX_GAP_MILLIS) return false
    val startMillis = endMillis - PREDICTED_HIGH_WINDOW_MILLIS

    val window = sorted.filter { it.timestamp.toEpochMilli() >= startMillis }
    if (window.size < MIN_READINGS) return false
    if (endMillis - window.first().timestamp.toEpochMilli() < MIN_COVERAGE_MILLIS) return false
    if (window.zipWithNext().any { (a, b) ->
            b.timestamp.toEpochMilli() - a.timestamp.toEpochMilli() > MAX_GAP_MILLIS
        }
    ) {
        return false
    }

    val low = alarms.low.thresholdMgDl
    val high = alarms.high.thresholdMgDl
    if (window.any { it.mgDl <= low || it.mgDl >= high }) return false

    val slopePerHour = slopeMgDlPerHour(window, endMillis)
    if (slopePerHour < MIN_SLOPE_MGDL_PER_HOUR || slopePerHour > MAX_SLOPE_MGDL_PER_HOUR) return false

    // Segment boundaries counted back from the newest reading, using the reading nearest each.
    val boundaries = (0..4).map { i ->
        val target = endMillis - (4 - i) * SEGMENT_MILLIS
        window.minByOrNull { abs(it.timestamp.toEpochMilli() - target) }
            ?.takeIf { abs(it.timestamp.toEpochMilli() - target) <= SEGMENT_MATCH_TOLERANCE_MILLIS }
            ?: return false
    }
    if (boundaries.zipWithNext().any { (a, b) ->
            (b.mgDl - a.mgDl) !in MIN_SEGMENT_DELTA_MGDL..MAX_SEGMENT_DELTA_MGDL
        }
    ) {
        return false
    }

    if (latest.mgDl + slopePerHour * (PROJECTION_MINUTES / 60.0) < high) return false

    val mealOrBolusInWindow = treatments.any { t ->
        val at = t.timestamp.toEpochMilli()
        at in startMillis..nowMillis &&
            ((t.carbsGrams ?: 0.0) > 0.0 ||
                (isBolusEventType(t.eventType) && !isSmbEventType(t.eventType)))
    }
    return !mealOrBolusInWindow
}

/** Least-squares slope of glucose over time, in mg/dL per hour. Time is measured in minutes
 *  relative to [endMillis] to keep the arithmetic well-conditioned. */
private fun slopeMgDlPerHour(window: List<GlucoseReading>, endMillis: Long): Double {
    val xs = window.map { (it.timestamp.toEpochMilli() - endMillis) / 60_000.0 }
    val ys = window.map { it.mgDl.toDouble() }
    val meanX = xs.average()
    val meanY = ys.average()
    var num = 0.0
    var den = 0.0
    for (i in xs.indices) {
        num += (xs[i] - meanX) * (ys[i] - meanY)
        den += (xs[i] - meanX) * (xs[i] - meanX)
    }
    return if (den == 0.0) 0.0 else num / den * 60.0
}
