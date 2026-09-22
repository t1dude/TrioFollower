package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.DayNightWindow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulingTest {
    // Day 07:00-22:00 (420-1320), Night 22:00-07:00 (1320-420, wraps past midnight).
    private val window = DayNightWindow()

    @Test
    fun alwaysAllowedRegardlessOfWindowGapsOrOverlaps() {
        // Both on is "no restriction": true at any minute, even one a gappy/overlapping window
        // configuration would otherwise leave uncovered.
        val always = AlarmBehavior(fireDay = true, fireNight = true)
        for (minute in listOf(0, 419, 420, 900, 1319, 1320, 1439)) {
            assertTrue("minute=$minute", isAllowedNow(always, window, minute))
        }
    }

    @Test
    fun neverAllowedWhenBothOff() {
        val never = AlarmBehavior(fireDay = false, fireNight = false)
        assertFalse(isAllowedNow(never, window, 720))
    }

    @Test
    fun dayOnlyMatchesOnlyTheDayWindow() {
        val dayOnly = AlarmBehavior(fireDay = true, fireNight = false)
        assertTrue(isAllowedNow(dayOnly, window, 720)) // noon
        assertFalse(isAllowedNow(dayOnly, window, 60)) // 01:00, night
        assertTrue(isAllowedNow(dayOnly, window, 420)) // 07:00 start is inclusive
        assertFalse(isAllowedNow(dayOnly, window, 1320)) // 22:00 end is exclusive
    }

    @Test
    fun nightOnlyWrapsPastMidnight() {
        val nightOnly = AlarmBehavior(fireDay = false, fireNight = true)
        assertTrue(isAllowedNow(nightOnly, window, 1320)) // 22:00
        assertTrue(isAllowedNow(nightOnly, window, 0)) // midnight
        assertTrue(isAllowedNow(nightOnly, window, 419)) // 06:59
        assertFalse(isAllowedNow(nightOnly, window, 420)) // 07:00, day starts
        assertFalse(isAllowedNow(nightOnly, window, 720)) // noon
    }
}
