package com.trionsandroid.app.data.nightscout

import java.time.Duration
import java.time.Instant

// Assumed lifetimes, since Nightscout has no pump or sensor expiry field: 3 days for a pump site
// (cannula), 10 days for a CGM sensor. Shared by the HUD display (GlucoseHud) and the sensor/pump
// change alarms (AlarmCheckRunner) so both agree on how "time left" is computed.
const val SITE_CHANGE_INTERVAL_DAYS = 3L
const val SENSOR_DURATION_DAYS = 10L

private fun latestEventAt(treatments: List<Treatment>, eventType: String): Instant? =
    treatments.filter { it.eventType == eventType }.maxByOrNull { it.timestamp.toEpochMilli() }?.timestamp

/** Time left until the pump site (cannula) is due for a change, or null if none is logged. */
fun siteTimeRemaining(treatments: List<Treatment>, now: Instant): Duration? =
    latestEventAt(treatments, SITE_CHANGE_EVENT_TYPE)?.let { Duration.ofDays(SITE_CHANGE_INTERVAL_DAYS) - Duration.between(it, now) }

/** Time left until the CGM sensor is due for a change, or null if none is logged. */
fun sensorTimeRemaining(treatments: List<Treatment>, now: Instant): Duration? =
    latestEventAt(treatments, SENSOR_START_EVENT_TYPE)?.let { Duration.ofDays(SENSOR_DURATION_DAYS) - Duration.between(it, now) }

/** "2d 4h", "6h 30m", "45m", or "Replace" once expired/overdue. Shared by the HUD and the sensor/pump change alarms. */
fun formatTimeRemaining(remaining: Duration): String {
    if (remaining.isNegative || remaining.isZero) return "Replace"
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    return when {
        days >= 1 -> "${days}d ${hours}h"
        hours >= 1 -> if (hours < 12) "${hours}h ${minutes}m" else "${hours}h"
        else -> "${minutes}m"
    }
}
