package com.trionsandroid.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.trionsandroid.app.data.nightscout.ForecastType

/** Forecast line colors (ARGB) from Trio: IOB is its insulin blue, ZT and UAM come from its asset catalog. */
fun forecastLineColor(type: ForecastType): Int = when (type) {
    ForecastType.IOB -> TrioInsulin.toArgb()
    ForecastType.ZT -> 0xFF7161EF.toInt()
    ForecastType.COB -> 0xFFFF9500.toInt()
    ForecastType.UAM -> 0xFFD12BF7.toInt()
}
