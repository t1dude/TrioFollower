package com.trionsandroid.app.data.settings

import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

enum class TimeFormat(val label: String) {
    HOUR_12("12-hour"),
    HOUR_24("24-hour"),
}

// The pattern letter "a" renders the locale's own AM/PM text, which in many locales is "a.m."/
// "p.m." (with periods) rather than "AM"/"PM" — wider than needed in a compact chart tick or
// notification. Force plain lowercase "am"/"pm" regardless of locale instead.
private val AM_PM_TEXT = mapOf(0L to "am", 1L to "pm")

private fun DateTimeFormatterBuilder.appendPatternThenAmPm(pattern: String): DateTimeFormatter =
    appendPattern(pattern).appendText(ChronoField.AMPM_OF_DAY, AM_PM_TEXT).toFormatter()

/** Just the time, e.g. "14:05" (24-hour) or "2:05pm" (12-hour). */
fun TimeFormat.timeFormatter(): DateTimeFormatter = when (this) {
    TimeFormat.HOUR_24 -> DateTimeFormatter.ofPattern("HH:mm")
    TimeFormat.HOUR_12 -> DateTimeFormatterBuilder().appendPatternThenAmPm("h:mm")
}

/** Date + time, e.g. "18.09 14:05" or "18.09 2:05pm". */
fun TimeFormat.dateTimeFormatter(): DateTimeFormatter = when (this) {
    TimeFormat.HOUR_24 -> DateTimeFormatter.ofPattern("dd.MM HH:mm")
    TimeFormat.HOUR_12 -> DateTimeFormatterBuilder().appendPatternThenAmPm("dd.MM h:mm")
}

/** Hour-only chart axis tick, e.g. "14" or "2pm". */
fun TimeFormat.hourFormatter(): DateTimeFormatter = when (this) {
    TimeFormat.HOUR_24 -> DateTimeFormatter.ofPattern("HH")
    TimeFormat.HOUR_12 -> DateTimeFormatterBuilder().appendPatternThenAmPm("h")
}
