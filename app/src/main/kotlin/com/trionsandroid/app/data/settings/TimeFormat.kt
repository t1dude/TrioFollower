package com.trionsandroid.app.data.settings

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

enum class TimeFormat(val label: String) {
    HOUR_12("12-hour"),
    HOUR_24("24-hour"),
}

// Pattern "a" gives locale text like "a.m."; use plain "am"/"pm" instead.
private val AM_PM_TEXT = mapOf(0L to "am", 1L to "pm")

private fun DateTimeFormatterBuilder.appendPatternThenAmPm(pattern: String): DateTimeFormatter =
    appendPattern(pattern).appendText(ChronoField.AMPM_OF_DAY, AM_PM_TEXT).toFormatter()

fun TimeFormat.timeFormatter(): DateTimeFormatter = when (this) {
    TimeFormat.HOUR_24 -> DateTimeFormatter.ofPattern("HH:mm")
    TimeFormat.HOUR_12 -> DateTimeFormatterBuilder().appendPatternThenAmPm("h:mm")
}

fun TimeFormat.hourFormatter(): DateTimeFormatter = when (this) {
    TimeFormat.HOUR_24 -> DateTimeFormatter.ofPattern("HH")
    TimeFormat.HOUR_12 -> DateTimeFormatterBuilder().appendPatternThenAmPm("h")
}
