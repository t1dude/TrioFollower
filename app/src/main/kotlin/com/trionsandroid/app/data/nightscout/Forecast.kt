package com.trionsandroid.app.data.nightscout

enum class ForecastType { IOB, ZT, COB, UAM }

/** The loop's latest forecast: mg/dL values per curve, every 5 minutes from [startMillis] (deliverAt). */
data class Forecast(
    val startMillis: Long,
    val series: Map<ForecastType, List<Int>>,
) {
    fun timeMillisAt(index: Int): Long = startMillis + index * FORECAST_STEP_MILLIS

    /**
     * Highest and lowest value across the curves at [index] (only curves that reach it). A flat
     * envelope gets a ±1 band so the cone stays visible. Used by the chart and the widget.
     */
    fun envelopeAt(index: Int): Pair<Int, Int> {
        val values = series.values.filter { index < it.size }.map { it[index] }
        val high = values.max()
        val low = values.min()
        return if (high == low) (high + 1) to (low - 1) else high to low
    }

    companion object {
        const val FORECAST_STEP_MILLIS = 300_000L

        // As in Trio: nothing is drawn past 2.5h ahead, and the cone uses at least an hour of steps.
        const val MAX_AHEAD_MILLIS = 150 * 60_000L
        const val CONE_MIN_POINTS = 12
    }
}
