package com.trionsandroid.app.data.settings

/** How (or whether) the loop's glucose forecast is drawn on the chart — mirrors Trio's
 *  Forecast Display Type, plus an Off choice. */
enum class ForecastDisplay(val label: String) {
    OFF("Off"),
    LINES("Lines"),
    CONE("Cone"),
}
