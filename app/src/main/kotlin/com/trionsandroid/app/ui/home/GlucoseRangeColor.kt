package com.trionsandroid.app.ui.home

import androidx.compose.ui.graphics.Color
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseColorScheme

// Ported from Trio's DynamicGlucoseColor.swift: hue runs red (0°) → green (120°) → purple (270°) at
// saturation 0.6 / brightness 0.9. In the dynamic scheme Trio's chart uses fixed 55/220 mg/dL
// endpoints (see GlucoseChartView.pointColor's workaround comment) with the profile target in the
// middle; we have no profile target on hand, so the target is a fixed 100 mg/dL.
private const val DYNAMIC_LOW_MGDL = 55
private const val DYNAMIC_HIGH_MGDL = 220
private const val DYNAMIC_TARGET_MGDL = 100

private fun hueColor(hueDegrees: Float) = Color.hsv(hueDegrees, 0.6f, 0.9f)
private val STATIC_LOW = hueColor(0f)
private val STATIC_IN_RANGE = hueColor(120f)
private val STATIC_HIGH = hueColor(270f)

private fun dynamicColor(mgDl: Int): Color {
    val hue = when {
        mgDl <= DYNAMIC_LOW_MGDL -> 0f
        mgDl >= DYNAMIC_HIGH_MGDL -> 270f
        mgDl <= DYNAMIC_TARGET_MGDL ->
            (mgDl - DYNAMIC_LOW_MGDL).toFloat() / (DYNAMIC_TARGET_MGDL - DYNAMIC_LOW_MGDL) * 120f
        else ->
            120f + (mgDl - DYNAMIC_TARGET_MGDL).toFloat() / (DYNAMIC_HIGH_MGDL - DYNAMIC_TARGET_MGDL) * 150f
    }
    return hueColor(hue)
}

/** Colors a glucose value per the user's chosen [scheme]. The static scheme's low/high bounds are
 *  the user's own alarm thresholds from Settings. */
fun rangeColor(mgDl: Int, alarms: AlarmSettings, scheme: GlucoseColorScheme): Color = when (scheme) {
    GlucoseColorScheme.DYNAMIC -> dynamicColor(mgDl)
    GlucoseColorScheme.STATIC -> when {
        mgDl <= alarms.low.thresholdMgDl -> STATIC_LOW
        mgDl >= alarms.high.thresholdMgDl -> STATIC_HIGH
        else -> STATIC_IN_RANGE
    }
}
