package com.trionsandroid.app.data.alarm

import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.GlucoseTrend
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.AlarmSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PredictedHighEvaluatorTest {
    private val alarms = AlarmSettings() // low 70, high 180
    private val end = Instant.parse("2026-09-21T08:00:00Z")

    /** One reading every 5 minutes for the last hour, rising by [stepMgDl] each time. */
    private fun rising(startMgDl: Int, stepMgDl: Double): List<GlucoseReading> =
        (0..12).map { i ->
            GlucoseReading(
                id = "r$i",
                timestamp = end.minusSeconds((12 - i) * 300L),
                mgDl = (startMgDl + stepMgDl * i).toInt(),
                trend = GlucoseTrend.Flat,
            )
        }

    private val nowMillis = end.plusSeconds(60).toEpochMilli()

    @Test
    fun slowSteadyClimbTowardHighFires() {
        // 120 -> 150 over an hour: 30 mg/dL per hour, would reach 180 in the next hour.
        assertTrue(isPredictedHigh(rising(120, 2.5), emptyList(), alarms, nowMillis))
    }

    @Test
    fun fastRiseIsLeftToTheHighAlarm() {
        assertFalse(isPredictedHigh(rising(100, 5.0), emptyList(), alarms, nowMillis))
    }

    @Test
    fun flatGlucoseDoesNotFire() {
        assertFalse(isPredictedHigh(rising(120, 0.0), emptyList(), alarms, nowMillis))
    }

    @Test
    fun aBolusInTheWindowSuppressesIt() {
        val bolus = Treatment(
            id = "b",
            timestamp = end.minusSeconds(1800),
            eventType = "Correction Bolus",
            insulinUnits = 2.0,
            carbsGrams = null,
            durationMinutes = null,
            basalRateUnitsPerHour = null,
            notes = null,
            targetMgDl = null,
        )
        assertFalse(isPredictedHigh(rising(120, 2.5), listOf(bolus), alarms, nowMillis))
    }

    @Test
    fun anSmbDoesNotSuppressIt() {
        val smb = Treatment("s", end.minusSeconds(1800), "SMB", 0.3, null, null, null, null, null)
        assertTrue(isPredictedHigh(rising(120, 2.5), listOf(smb), alarms, nowMillis))
    }

    @Test
    fun tooLittleDataDoesNotFire() {
        assertFalse(isPredictedHigh(rising(120, 2.5).takeLast(4), emptyList(), alarms, nowMillis))
    }
}
