package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.DayNightWindow

/**
 * Whether an alarm with [behavior] is allowed to fire right now ([nowMinuteOfDay], 0-1439), given
 * the user's Day/Night [window]. An alarm that allows both Day and Night has no time restriction at
 * all, so a gap or overlap between the user's own Day and Night windows can never silently block it;
 * only an alarm deliberately restricted to just one of them checks whether now actually falls in it.
 */
fun isAllowedNow(behavior: AlarmBehavior, window: DayNightWindow, nowMinuteOfDay: Int): Boolean {
    if (behavior.fireDay && behavior.fireNight) return true
    if (!behavior.fireDay && !behavior.fireNight) return false
    val isDay = nowMinuteOfDay.isWithinMinuteRange(window.dayStartMinute, window.dayEndMinute)
    val isNight = nowMinuteOfDay.isWithinMinuteRange(window.nightStartMinute, window.nightEndMinute)
    return (behavior.fireDay && isDay) || (behavior.fireNight && isNight)
}

/** Minute-of-day range containment, wrapping past midnight when end <= start (e.g. 22:00-07:00). */
private fun Int.isWithinMinuteRange(startMinute: Int, endMinute: Int): Boolean =
    if (startMinute <= endMinute) this in startMinute until endMinute else this >= startMinute || this < endMinute
