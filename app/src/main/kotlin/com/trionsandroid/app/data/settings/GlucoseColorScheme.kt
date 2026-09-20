package com.trionsandroid.app.data.settings

/** How glucose values are colored on the chart, bubble and History — mirrors Trio's Glucose
 *  Color Scheme (GlucoseColorScheme.swift). */
enum class GlucoseColorScheme(val label: String) {
    /** Trio's default: a red → green → purple hue gradient centered on the target. */
    DYNAMIC("Dynamic"),
    /** Red below range, green in range, purple above range. */
    STATIC("Static"),
}
