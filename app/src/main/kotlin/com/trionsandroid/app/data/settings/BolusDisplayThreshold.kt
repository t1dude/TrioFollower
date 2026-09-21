package com.trionsandroid.app.data.settings

/** Boluses smaller than [minUnits] still get a chart marker but no amount label. */
enum class BolusDisplayThreshold(val label: String, val minUnits: Double) {
    ALL("Show all", 0.0),
    FROM_0_1("0.1 U and over", 0.1),
    FROM_0_5("0.5 U and over", 0.5),
    FROM_1("1 U and over", 1.0),
}
