package com.trionsandroid.app.ui.home

import com.trionsandroid.app.data.nightscout.Treatment
import kotlin.math.exp
import kotlin.math.pow

data class IobPoint(val timestampMillis: Long, val iobUnits: Double)

const val DEFAULT_DIA_HOURS = 6.0
private const val DEFAULT_PEAK_MINUTES = 75.0 // rapid-acting insulin default, matching oref0

/**
 * Computes total insulin-on-board at regular intervals across the viewport, using oref0's
 * exponential insulin activity model (github.com/openaps/oref0/blob/master/lib/iob/calculate.js
 * — the reference algorithm Trio's own loop engine descends from), evaluated once per bolus dose
 * and summed.
 *
 * Deliberately only accounts for bolus-type doses (insulinUnits > 0), not temp basal's net
 * contribution above/below the scheduled rate — netting that correctly requires knowing the
 * full day's schedule to compare against, and most of what a user cares about visually here is
 * bolus-driven anyway. A documented simplification, not an oversight.
 */
fun computeIobSeries(
    viewportStartMillis: Long,
    viewportEndMillis: Long,
    treatments: List<Treatment>,
    diaHours: Double,
    sampleIntervalMillis: Long,
): List<IobPoint> {
    if (viewportEndMillis <= viewportStartMillis || sampleIntervalMillis <= 0) return emptyList()
    val endMinutes = diaHours * 60.0
    if (endMinutes <= 0) return emptyList()

    val diaMillis = (endMinutes * 60_000).toLong()
    val relevantDoses = treatments.filter { dose ->
        val units = dose.insulinUnits
        val doseMillis = dose.timestamp.toEpochMilli()
        units != null && units > 0.0 &&
            doseMillis <= viewportEndMillis &&
            doseMillis >= viewportStartMillis - diaMillis
    }
    if (relevantDoses.isEmpty()) return emptyList()

    val points = mutableListOf<IobPoint>()
    var t = viewportStartMillis
    while (t <= viewportEndMillis) {
        var totalIob = 0.0
        relevantDoses.forEach { dose ->
            val minsAgo = (t - dose.timestamp.toEpochMilli()) / 60_000.0
            if (minsAgo in 0.0..endMinutes) {
                totalIob += iobContribution(dose.insulinUnits!!, minsAgo, endMinutes, DEFAULT_PEAK_MINUTES)
            }
        }
        points.add(IobPoint(t, totalIob))
        t += sampleIntervalMillis
    }
    return points
}

private fun iobContribution(insulinUnits: Double, minsAgo: Double, endMinutes: Double, peakMinutes: Double): Double {
    val tau = peakMinutes * (1 - peakMinutes / endMinutes) / (1 - 2 * peakMinutes / endMinutes)
    val a = 2 * tau / endMinutes
    val s = 1 / (1 - a + (1 + a) * exp(-endMinutes / tau))
    return insulinUnits * (
        1 - s * (1 - a) * (
            (minsAgo.pow(2) / (tau * endMinutes * (1 - a)) - minsAgo / tau - 1) * exp(-minsAgo / tau) + 1
            )
        )
}
