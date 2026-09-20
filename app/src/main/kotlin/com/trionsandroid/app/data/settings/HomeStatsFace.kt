package com.trionsandroid.app.data.settings

/** What the Home statistics bar shows (as in Trio). */
enum class HomeStatsFace(val label: String) {
    TIME_IN_RANGE("Time in Range"),
    DISTRIBUTION_BAR("Distribution bar only"),
    AVERAGES("Today's averages"),
    HIDDEN("Hidden"),
}
