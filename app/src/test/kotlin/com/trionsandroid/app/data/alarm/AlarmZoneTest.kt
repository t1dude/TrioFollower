package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.AlarmSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmZoneTest {
    private val defaults = AlarmSettings() // urgentLow 55, low 70, high 180, urgentHigh 250, all always-on
    private val noon = 720 // within the default Day window (07:00-22:00)
    private val midnight = 0 // within the default Night window (22:00-07:00)

    @Test
    fun picksTheMostSevereApplicableTier() {
        assertEquals(AlarmZone.URGENT_LOW, evaluateAlarmZone(50, defaults, noon))
        assertEquals(AlarmZone.LOW, evaluateAlarmZone(65, defaults, noon))
        assertEquals(AlarmZone.NORMAL, evaluateAlarmZone(100, defaults, noon))
        assertEquals(AlarmZone.HIGH, evaluateAlarmZone(200, defaults, noon))
        assertEquals(AlarmZone.URGENT_HIGH, evaluateAlarmZone(260, defaults, noon))
    }

    @Test
    fun aDisabledTierFallsThroughToTheNextOne() {
        val noUrgentLow = defaults.copy(urgentLow = defaults.urgentLow.copy(enabled = false))
        assertEquals(AlarmZone.LOW, evaluateAlarmZone(40, noUrgentLow, noon))
    }

    @Test
    fun aTierRestrictedToDayFallsThroughAtNight() {
        val dayOnlyLow = defaults.copy(low = defaults.low.copy(behavior = AlarmBehavior(fireDay = true, fireNight = false)))
        assertEquals(AlarmZone.LOW, evaluateAlarmZone(65, dayOnlyLow, noon))
        // Same glucose value, but it's night and Low isn't allowed to fire then: falls through past
        // it. Urgent low is still on both day and night by default, so it doesn't apply here either
        // (65 is above its 55 threshold) and the result is NORMAL, not a silent promotion to urgent.
        assertEquals(AlarmZone.NORMAL, evaluateAlarmZone(65, dayOnlyLow, midnight))
    }

    @Test
    fun anAlwaysOnTierIgnoresTheWindowEntirely() {
        // Both fireDay and fireNight true (the default) is never restricted, even checked at a
        // minute a gap or overlap in the user's own Day/Night ranges could otherwise leave uncovered.
        val gappy = defaults.copy(dayNightWindow = defaults.dayNightWindow.copy(dayEndMinute = 500, nightStartMinute = 1400))
        assertEquals(AlarmZone.URGENT_HIGH, evaluateAlarmZone(300, gappy, 1000)) // in the gap between the two windows
    }

    @Test
    fun behaviorForMapsEachZoneToItsOwnAlarmsBehavior() {
        val settings = defaults.copy(
            low = defaults.low.copy(behavior = AlarmBehavior(soundEnabled = false)),
            high = defaults.high.copy(behavior = AlarmBehavior(vibrationEnabled = false)),
        )
        assertEquals(false, settings.behaviorFor(AlarmZone.LOW).soundEnabled)
        assertEquals(true, settings.behaviorFor(AlarmZone.HIGH).soundEnabled)
        assertEquals(false, settings.behaviorFor(AlarmZone.HIGH).vibrationEnabled)
    }
}
