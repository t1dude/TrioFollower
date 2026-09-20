package com.trionsandroid.app.data.nightscout

/** oref's forecast curves, keyed the way Trio names them (predBGs.IOB / ZT / COB / UAM). */
enum class ForecastType { IOB, ZT, COB, UAM }

/**
 * The loop's latest glucose forecast: one list of mg/dL values per available [ForecastType], one
 * value every 5 minutes starting at [startMillis] (the determination's own deliverAt, matching
 * Trio's ForecastView).
 */
data class Forecast(
    val startMillis: Long,
    val series: Map<ForecastType, List<Int>>,
) {
    fun timeMillisAt(index: Int): Long = startMillis + index * FORECAST_STEP_MILLIS

    companion object {
        const val FORECAST_STEP_MILLIS = 300_000L
    }
}
