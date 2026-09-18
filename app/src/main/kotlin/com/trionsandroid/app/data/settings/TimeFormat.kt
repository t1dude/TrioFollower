package com.trionsandroid.app.data.settings

enum class TimeFormat(val label: String) {
    HOUR_12("12-hour"),
    HOUR_24("24-hour"),
}

/** Just the time, e.g. "14:05" (24-hour) or "2:05 PM" (12-hour). */
fun TimeFormat.timePattern(): String = when (this) {
    TimeFormat.HOUR_24 -> "HH:mm"
    TimeFormat.HOUR_12 -> "h:mm a"
}

/** Date + time, e.g. "18.09 14:05" or "18.09 2:05 PM". */
fun TimeFormat.dateTimePattern(): String = "dd.MM ${timePattern()}"

/** Hour-only chart axis tick, e.g. "14" or "2 PM". */
fun TimeFormat.hourPattern(): String = when (this) {
    TimeFormat.HOUR_24 -> "HH"
    TimeFormat.HOUR_12 -> "h a"
}
