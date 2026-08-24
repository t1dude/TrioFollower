package com.trionsandroid.app.data.settings

enum class BackgroundMode(val label: String, val description: String) {
    WORK_MANAGER(
        label = "Battery friendly",
        description = "Periodic background sync via WorkManager. Minimum interval 15 minutes.",
    ),
    FOREGROUND_SERVICE(
        label = "Real-time",
        description = "A persistent foreground service polls more often for near-real-time alarms. " +
            "Shows an ongoing notification and uses more battery.",
    ),
}

/** Refresh intervals (minutes) each background mode can reliably support. */
fun BackgroundMode.allowedRefreshIntervals(): List<Int> = when (this) {
    BackgroundMode.WORK_MANAGER -> listOf(15, 30, 60)
    BackgroundMode.FOREGROUND_SERVICE -> listOf(1, 5, 15, 30)
}
