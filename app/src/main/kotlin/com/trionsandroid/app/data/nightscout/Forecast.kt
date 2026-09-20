package com.trionsandroid.app.data.nightscout

enum class ForecastType { IOB, ZT, COB, UAM }

/** The loop's latest forecast: mg/dL values per curve, every 5 minutes from [startMillis] (deliverAt). */
data class Forecast(
    val startMillis: Long,
    val series: Map<ForecastType, List<Int>>,
) {
    fun timeMillisAt(index: Int): Long = startMillis + index * FORECAST_STEP_MILLIS

    companion object {
        const val FORECAST_STEP_MILLIS = 300_000L
    }
}
