package com.trionsandroid.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Y_GRIDLINES_MGDL = listOf(50, 100, 150, 200, 250, 300)
private const val Y_MIN_MGDL = 40f
private const val Y_MAX_MGDL = 300f
private const val WINDOW_HOURS = 6L
private val hourFormatter = DateTimeFormatter.ofPattern("HH")

/**
 * A static (non-scrollable, non-zoomable) view of the most recent [WINDOW_HOURS] of glucose
 * readings — a first pass before the real scrollable/zoomable chart with the insulin overlay.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val nowMillis = System.currentTimeMillis()
        val windowStartMillis = nowMillis - WINDOW_HOURS * 60 * 60 * 1000L
        val sorted = readings
            .filter { it.timestamp.toEpochMilli() >= windowStartMillis }
            .sortedBy { it.timestamp }

        val leftGutter = 40.dp.toPx()
        val bottomGutter = 20.dp.toPx()
        val chartWidth = size.width - leftGutter
        val chartHeight = size.height - bottomGutter

        fun xFor(millis: Long): Float =
            leftGutter + ((millis - windowStartMillis).toFloat() / (nowMillis - windowStartMillis).toFloat()) * chartWidth

        fun yFor(mgDl: Int): Float {
            val clamped = mgDl.toFloat().coerceIn(Y_MIN_MGDL, Y_MAX_MGDL)
            return chartHeight - ((clamped - Y_MIN_MGDL) / (Y_MAX_MGDL - Y_MIN_MGDL)) * chartHeight
        }

        // Horizontal gridlines + value labels
        Y_GRIDLINES_MGDL.forEach { mgDl ->
            val y = yFor(mgDl)
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(leftGutter, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = textMeasurer.measure(unit.format(mgDl), style = TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
        }

        // Dashed alarm threshold lines, reusing the user's own Settings thresholds
        val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        if (alarms.low.enabled) {
            val y = yFor(alarms.low.thresholdMgDl)
            drawLine(
                color = TrioGlucoseLow,
                start = Offset(leftGutter, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }
        if (alarms.high.enabled) {
            val y = yFor(alarms.high.thresholdMgDl)
            drawLine(
                color = TrioGlucoseHigh,
                start = Offset(leftGutter, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dash,
            )
        }

        // Vertical hour ticks + labels
        val zone = ZoneId.systemDefault()
        var tick = Instant.ofEpochMilli(windowStartMillis).atZone(zone).withMinute(0).withSecond(0).withNano(0)
        if (tick.toInstant().toEpochMilli() < windowStartMillis) tick = tick.plusHours(1)
        while (tick.toInstant().toEpochMilli() <= nowMillis) {
            val x = xFor(tick.toInstant().toEpochMilli())
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(x, 0f),
                end = Offset(x, chartHeight),
                strokeWidth = 1.dp.toPx(),
            )
            val label = textMeasurer.measure(hourFormatter.format(tick), style = TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(label, topLeft = Offset(x - label.size.width / 2f, chartHeight + 4.dp.toPx()))
            tick = tick.plusHours(1)
        }

        // Connect consecutive readings
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(xFor(a.timestamp.toEpochMilli()), yFor(a.mgDl)),
                end = Offset(xFor(b.timestamp.toEpochMilli()), yFor(b.mgDl)),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        // Reading dots, colored by range
        sorted.forEach { reading ->
            drawCircle(
                color = rangeColor(reading.mgDl, alarms),
                radius = 3.dp.toPx(),
                center = Offset(xFor(reading.timestamp.toEpochMilli()), yFor(reading.mgDl)),
            )
        }
    }
}
