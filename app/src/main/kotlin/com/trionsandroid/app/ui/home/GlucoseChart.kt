package com.trionsandroid.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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

private const val HOUR_MILLIS = 3_600_000L
private const val DEFAULT_VIEWPORT_MILLIS = 6 * HOUR_MILLIS
private const val MIN_VIEWPORT_MILLIS = 30 * 60_000L
private val LEFT_GUTTER = 40.dp
private val BOTTOM_GUTTER = 20.dp
private val hourFormatter = DateTimeFormatter.ofPattern("HH")
private val dayFormatter = DateTimeFormatter.ofPattern("dd.MM")

/**
 * A pannable, pinch-zoomable view of glucose history. The visible window is clamped to
 * whatever's actually cached locally (see HomeViewModel's OBSERVE_WINDOW_HOURS) — scrolling
 * back further than that isn't possible yet, since refresh() only actively re-fetches the last
 * 24h; deeper on-demand history fetching is a future enhancement.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant

    val nowMillis = System.currentTimeMillis()
    val dataMinMillis = readings.minOfOrNull { it.timestamp.toEpochMilli() } ?: (nowMillis - DEFAULT_VIEWPORT_MILLIS)
    val dataMaxMillis = nowMillis
    val maxViewportMillis = (dataMaxMillis - dataMinMillis).coerceAtLeast(DEFAULT_VIEWPORT_MILLIS)

    var viewportEndMillis by remember { mutableLongStateOf(dataMaxMillis) }
    var viewportDurationMillis by remember { mutableLongStateOf(DEFAULT_VIEWPORT_MILLIS) }
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }

    val leftGutterPx = with(density) { LEFT_GUTTER.toPx() }

    // dataMinMillis/dataMaxMillis change on essentially every recomposition (dataMaxMillis
    // tracks wall-clock "now"), so they can't be the pointerInput key without restarting the
    // gesture detector mid-gesture. rememberUpdatedState lets the long-lived gesture callback
    // below always read the current value without the detector itself ever restarting.
    val currentDataMinMillis by rememberUpdatedState(dataMinMillis)
    val currentDataMaxMillis by rememberUpdatedState(dataMaxMillis)
    val currentMaxViewportMillis by rememberUpdatedState(maxViewportMillis)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val chartWidthPx = canvasWidthPx - leftGutterPx
                    if (chartWidthPx <= 0f) return@detectTransformGestures

                    val oldDuration = viewportDurationMillis
                    val oldStart = viewportEndMillis - oldDuration
                    val centroidFraction = ((centroid.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                    val centroidTimeMillis = oldStart + (centroidFraction * oldDuration).toLong()

                    val newDuration = (oldDuration / zoom)
                        .toLong()
                        .coerceIn(MIN_VIEWPORT_MILLIS, currentMaxViewportMillis)

                    var newStart = centroidTimeMillis - (centroidFraction * newDuration).toLong()
                    val millisPerPx = newDuration / chartWidthPx
                    newStart -= (pan.x * millisPerPx).toLong()
                    var newEnd = newStart + newDuration

                    if (newStart < currentDataMinMillis) {
                        newEnd += currentDataMinMillis - newStart
                        newStart = currentDataMinMillis
                    }
                    if (newEnd > currentDataMaxMillis) {
                        newStart -= newEnd - currentDataMaxMillis
                        newEnd = currentDataMaxMillis
                    }
                    newStart = newStart.coerceAtLeast(currentDataMinMillis)

                    viewportDurationMillis = newEnd - newStart
                    viewportEndMillis = newEnd
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val viewportStartMillis = viewportEndMillis - viewportDurationMillis
            val sorted = readings
                .filter { it.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis }
                .sortedBy { it.timestamp }

            val leftGutter = LEFT_GUTTER.toPx()
            val bottomGutter = BOTTOM_GUTTER.toPx()
            val chartWidth = size.width - leftGutter
            val chartHeight = size.height - bottomGutter

            fun xFor(millis: Long): Float =
                leftGutter + ((millis - viewportStartMillis).toFloat() / viewportDurationMillis) * chartWidth

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

            // Vertical time ticks + labels — granularity adapts to how far zoomed out we are
            val tickIntervalHours = when {
                viewportDurationMillis <= 12 * HOUR_MILLIS -> 1L
                viewportDurationMillis <= 3 * 24 * HOUR_MILLIS -> 6L
                else -> 24L
            }
            val useDayLabel = tickIntervalHours >= 24L
            val zone = ZoneId.systemDefault()
            var tick = Instant.ofEpochMilli(viewportStartMillis).atZone(zone)
                .withMinute(0).withSecond(0).withNano(0)
                .let { aligned ->
                    val hourOfAlignment = aligned.hour.toLong()
                    aligned.minusHours(hourOfAlignment % tickIntervalHours)
                }
            if (tick.toInstant().toEpochMilli() < viewportStartMillis) tick = tick.plusHours(tickIntervalHours)
            while (tick.toInstant().toEpochMilli() <= viewportEndMillis) {
                val x = xFor(tick.toInstant().toEpochMilli())
                drawLine(
                    color = gridColor.copy(alpha = 0.2f),
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )
                val text = if (useDayLabel) dayFormatter.format(tick) else hourFormatter.format(tick)
                val label = textMeasurer.measure(text, style = TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(label, topLeft = Offset(x - label.size.width / 2f, chartHeight + 4.dp.toPx()))
                tick = tick.plusHours(tickIntervalHours)
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
}
