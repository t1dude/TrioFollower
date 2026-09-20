package com.trionsandroid.app.data.settings

/** How the glucose forecast is drawn: like Trio's Forecast Display Type, plus Off. */
enum class ForecastDisplay(val label: String) {
    OFF("Off"),
    LINES("Lines"),
    CONE("Cone"),
}
