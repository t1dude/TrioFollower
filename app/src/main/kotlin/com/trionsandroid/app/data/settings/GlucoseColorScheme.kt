package com.trionsandroid.app.data.settings

/** How glucose values are colored (as in Trio's Glucose Color Scheme). */
enum class GlucoseColorScheme(val label: String) {
    /** Hue gradient from red through green to purple around the target. */
    DYNAMIC("Dynamic"),
    /** Red below range, green in range, purple above. */
    STATIC("Static"),
}
