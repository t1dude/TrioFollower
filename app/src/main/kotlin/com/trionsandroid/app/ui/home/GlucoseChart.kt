package com.trionsandroid.app.ui.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trionsandroid.app.data.nightscout.DeviceStatusPoint
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.nightscout.isAdjustmentEventType
import com.trionsandroid.app.data.nightscout.isBolusEventType
import com.trionsandroid.app.data.nightscout.isTempTargetEventType
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.hourFormatter
import com.trionsandroid.app.ui.theme.TrioAccentPurple
import com.trionsandroid.app.ui.theme.TrioBasal
import com.trionsandroid.app.ui.theme.TrioBolus
import com.trionsandroid.app.ui.theme.TrioCob
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioInsulin
import com.trionsandroid.app.ui.theme.TrioIob
import com.trionsandroid.app.ui.theme.TrioLoopGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.trionsandroid.app.data.nightscout.Forecast
import com.trionsandroid.app.data.nightscout.ForecastType
import com.trionsandroid.app.data.settings.BolusDisplayThreshold
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Y_GRIDLINES_MGDL = listOf(50, 100, 150, 200, 250, 300)
private const val Y_MIN_MGDL = 40f
private const val Y_MAX_MGDL = 300f

private const val HOUR_MILLIS = 3_600_000L
private const val DEFAULT_VIEWPORT_MILLIS = 6 * HOUR_MILLIS
private const val MIN_VIEWPORT_MILLIS = 30 * 60_000L
private val ZOOM_CYCLE_MILLIS = listOf(12 * HOUR_MILLIS, 6 * HOUR_MILLIS, 3 * HOUR_MILLIS)
private const val MIN_FLING_VELOCITY_PX_PER_SEC = 50f
private val LEFT_GUTTER = 40.dp
private val BOTTOM_LABEL_GAP = 4.dp
private val BOTTOM_SAFETY_MARGIN = 6.dp
private const val SCROLL_TO_LATEST_MILLIS = 700
private const val LIVE_EDGE_TOLERANCE_MILLIS = 2_000L

// Trio draws nothing past 2.5h ahead; the cone uses at least an hour of steps.
private const val FORECAST_MAX_AHEAD_MILLIS = 150 * 60_000L
private const val FORECAST_CONE_MIN_POINTS = 12

// Line colors from Trio: IOB is its insulin blue, ZT and UAM come from its asset catalog.
private fun forecastColor(type: ForecastType): Color = when (type) {
    ForecastType.IOB -> TrioInsulin
    ForecastType.ZT -> Color(0xFF7161EF)
    ForecastType.COB -> Color(0xFFFF9500)
    ForecastType.UAM -> Color(0xFFD12BF7)
}

private val dayFormatter = DateTimeFormatter.ofPattern("dd.MM")

private val BASAL_STRIP_HEIGHT = 40.dp
private val STRIP_TO_GLUCOSE_GAP = 8.dp
private val GLUCOSE_AREA_HEIGHT = 180.dp
private val GLUCOSE_TO_IOB_GAP = 8.dp
private val IOB_STRIP_HEIGHT = 50.dp
private val BOLUS_MARKER_TOP_MARGIN = 10.dp
private val ADJUSTMENT_BAND_TOP_MARGIN = 10.dp
private val ADJUSTMENT_LANE_HEIGHT = 22.dp

private const val IOB_ESTIMATE_SAMPLE_INTERVAL_MILLIS = 5 * 60_000L

/** Stretches of the viewport with no devicestatus IOB point nearby, to fill with a local estimate. */
private fun findIobGaps(
    viewportStartMillis: Long,
    viewportEndMillis: Long,
    realTimestampsMillis: List<Long>,
): List<LongRange> {
    val gaps = mutableListOf<LongRange>()
    var cursor = viewportStartMillis
    for (t in realTimestampsMillis) {
        if (t - cursor > IOB_GAP_THRESHOLD_MILLIS) gaps += cursor..t
        if (t > cursor) cursor = t
    }
    if (viewportEndMillis - cursor > IOB_GAP_THRESHOLD_MILLIS) gaps += cursor..viewportEndMillis
    return gaps
}

private fun formatBolusUnits(units: Double): String {
    if (units == units.toLong().toDouble()) return units.toLong().toString()
    return String.format(Locale.getDefault(), "%.2f", units).trimEnd('0').trimEnd('.')
}

/**
 * Pannable, pinch-zoomable chart with fling and double-tap zoom (12h/6h/3h). Bands from top to
 * bottom: basal, glucose, IOB/COB. The visible window is limited to the locally cached history.
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    treatments: List<Treatment>,
    insulinProfile: InsulinProfile?,
    deviceStatusPoints: List<DeviceStatusPoint>,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    colorScheme: GlucoseColorScheme = GlucoseColorScheme.DYNAMIC,
    showNowLine: Boolean = true,
    bolusDisplayThreshold: BolusDisplayThreshold = BolusDisplayThreshold.ALL,
    timeFormat: TimeFormat = TimeFormat.HOUR_24,
    forecast: Forecast? = null,
    forecastDisplay: ForecastDisplay = ForecastDisplay.OFF,
    scrollToLatestKey: Int = 0,
    forceScrollToLatest: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hourFormatter = remember(timeFormat) { timeFormat.hourFormatter() }
    val coroutineScope = rememberCoroutineScope()

    // Size the bottom margin from the measured label height instead of a fixed guess.
    val axisLabelHeightDp = remember(textMeasurer) {
        with(density) {
            textMeasurer.measure("00", style = TextStyle(fontSize = 10.sp)).size.height.toDp()
        }
    }
    val chartHeight = BASAL_STRIP_HEIGHT + STRIP_TO_GLUCOSE_GAP + GLUCOSE_AREA_HEIGHT +
        GLUCOSE_TO_IOB_GAP + IOB_STRIP_HEIGHT + BOTTOM_LABEL_GAP + axisLabelHeightDp + BOTTOM_SAFETY_MARGIN

    val nowMillis = System.currentTimeMillis()
    val dataMinMillis = readings.minOfOrNull { it.timestamp.toEpochMilli() } ?: (nowMillis - DEFAULT_VIEWPORT_MILLIS)
    // With a forecast showing, the right edge extends past "now" to the end of the forecast.
    val forecastEndMillis = if (forecastDisplay != ForecastDisplay.OFF && forecast != null) {
        forecast.timeMillisAt(forecast.series.values.maxOf { it.size } - 1).coerceAtMost(nowMillis + FORECAST_MAX_AHEAD_MILLIS)
    } else {
        nowMillis
    }
    val futureMillis = (forecastEndMillis - nowMillis).coerceAtLeast(0L)
    val dataMaxMillis = nowMillis + futureMillis
    val maxViewportMillis = (dataMaxMillis - dataMinMillis).coerceAtLeast(DEFAULT_VIEWPORT_MILLIS)

    // Resting position: "now" plus a peek of forecast (up to a quarter of the visible span).
    fun liveEdgeMillis(durationMillis: Long) = System.currentTimeMillis() + minOf(futureMillis, durationMillis / 4)

    var viewportDurationMillis by remember { mutableLongStateOf(DEFAULT_VIEWPORT_MILLIS) }
    var viewportEndMillis by remember { mutableLongStateOf(liveEdgeMillis(DEFAULT_VIEWPORT_MILLIS)) }
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var zoomCycleIndex by remember { mutableIntStateOf(-1) }
    var lastFollowedEndMillis by remember { mutableLongStateOf(viewportEndMillis) }

    val leftGutterPx = with(density) { LEFT_GUTTER.toPx() }

    // These change on almost every recomposition, so they can't key pointerInput without
    // restarting the gesture detector. Read them through rememberUpdatedState instead.
    val currentDataMinMillis by rememberUpdatedState(dataMinMillis)
    val currentDataMaxMillis by rememberUpdatedState(dataMaxMillis)
    val currentMaxViewportMillis by rememberUpdatedState(maxViewportMillis)

    // Keeps targetTimeMillis at targetFraction of the chart width for the given duration, clamped
    // to the data range. Used by pinch, double-tap and fling.
    fun applyViewport(targetTimeMillis: Long, targetFraction: Float, requestedDurationMillis: Long) {
        val newDuration = requestedDurationMillis.coerceIn(MIN_VIEWPORT_MILLIS, currentMaxViewportMillis)
        var newStart = targetTimeMillis - (targetFraction * newDuration).toLong()
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

    // After each refresh, glide to the newest data. Unless forced, only when the viewport is still
    // at the live edge, so an automatic refresh doesn't pull the user out of history.
    LaunchedEffect(scrollToLatestKey, forecast?.startMillis, forecastDisplay) {
        val startEnd = viewportEndMillis
        val targetEnd = liveEdgeMillis(viewportDurationMillis)
        val atLiveEdge = startEnd >= lastFollowedEndMillis - LIVE_EDGE_TOLERANCE_MILLIS
        if ((forceScrollToLatest || atLiveEdge) && targetEnd != startEnd) {
            flingJob?.cancel()
            lastFollowedEndMillis = targetEnd
            animate(0f, 1f, animationSpec = tween(SCROLL_TO_LATEST_MILLIS, easing = FastOutSlowInEasing)) { fraction, _ ->
                viewportEndMillis = startEnd + ((targetEnd - startEnd) * fraction).toLong()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
            .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectChartGestures(
                    onTouchDown = {
                        flingJob?.cancel()
                    },
                    onGesture = { centroid, pan, zoom ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f) {
                            val oldDuration = viewportDurationMillis
                            val oldStart = viewportEndMillis - oldDuration
                            val centroidFraction = ((centroid.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                            val centroidTimeMillis = oldStart + (centroidFraction * oldDuration).toLong()
                            val requestedDuration = (oldDuration / zoom).toLong()
                            val newDurationForPanScale = requestedDuration
                                .coerceIn(MIN_VIEWPORT_MILLIS, currentMaxViewportMillis)
                            val millisPerPx = newDurationForPanScale / chartWidthPx
                            val adjustedTargetMillis = centroidTimeMillis - (pan.x * millisPerPx).toLong()
                            applyViewport(adjustedTargetMillis, centroidFraction, requestedDuration)
                        }
                    },
                    onFlingVelocity = { velocityPxPerSec ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f && abs(velocityPxPerSec) > MIN_FLING_VELOCITY_PX_PER_SEC) {
                            val durationAtFlingStart = viewportDurationMillis
                            val millisPerPx = durationAtFlingStart / chartWidthPx
                            flingJob = coroutineScope.launch {
                                var previousValue = 0f
                                AnimationState(initialValue = 0f, initialVelocity = velocityPxPerSec)
                                    .animateDecay(exponentialDecay()) {
                                        val deltaMillis = ((value - previousValue) * millisPerPx).toLong()
                                        previousValue = value
                                        var newEnd = viewportEndMillis - deltaMillis
                                        var newStart = newEnd - durationAtFlingStart
                                        var hitBoundary = false
                                        if (newStart < currentDataMinMillis) {
                                            newStart = currentDataMinMillis
                                            newEnd = newStart + durationAtFlingStart
                                            hitBoundary = true
                                        }
                                        if (newEnd > currentDataMaxMillis) {
                                            newEnd = currentDataMaxMillis
                                            newStart = newEnd - durationAtFlingStart
                                            hitBoundary = true
                                        }
                                        viewportEndMillis = newEnd
                                        if (hitBoundary) cancelAnimation()
                                    }
                            }
                        }
                    },
                    onDoubleTap = { tapPosition ->
                        val chartWidthPx = canvasWidthPx - leftGutterPx
                        if (chartWidthPx > 0f) {
                            zoomCycleIndex = (zoomCycleIndex + 1) % ZOOM_CYCLE_MILLIS.size
                            val oldDuration = viewportDurationMillis
                            val oldStart = viewportEndMillis - oldDuration
                            val tapFraction = ((tapPosition.x - leftGutterPx) / chartWidthPx).coerceIn(0f, 1f)
                            val tapTimeMillis = oldStart + (tapFraction * oldDuration).toLong()
                            applyViewport(tapTimeMillis, tapFraction, ZOOM_CYCLE_MILLIS[zoomCycleIndex])
                        }
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val viewportStartMillis = viewportEndMillis - viewportDurationMillis
            val sorted = readings
                .filter { it.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis }
                .sortedBy { it.timestamp }

            val leftGutter = LEFT_GUTTER.toPx()
            val chartWidth = size.width - leftGutter

            val basalStripPx = BASAL_STRIP_HEIGHT.toPx()
            val stripGapPx = STRIP_TO_GLUCOSE_GAP.toPx()
            val glucoseTop = basalStripPx + stripGapPx
            val glucoseBottom = glucoseTop + GLUCOSE_AREA_HEIGHT.toPx()
            val glucoseChartHeight = glucoseBottom - glucoseTop
            val iobTop = glucoseBottom + GLUCOSE_TO_IOB_GAP.toPx()
            val iobBottom = iobTop + IOB_STRIP_HEIGHT.toPx()

            fun xFor(millis: Long): Float =
                leftGutter + ((millis - viewportStartMillis).toFloat() / viewportDurationMillis) * chartWidth

            fun yFor(mgDl: Int): Float {
                val clamped = mgDl.toFloat().coerceIn(Y_MIN_MGDL, Y_MAX_MGDL)
                return glucoseBottom - ((clamped - Y_MIN_MGDL) / (Y_MAX_MGDL - Y_MIN_MGDL)) * glucoseChartHeight
            }

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

            // Dashed alarm threshold lines
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

            // Time ticks; spacing adapts to the zoom level.
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
                    end = Offset(x, iobBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val text = if (useDayLabel) dayFormatter.format(tick) else hourFormatter.format(tick)
                val label = textMeasurer.measure(text, style = TextStyle(fontSize = 10.sp, color = labelColor))
                drawText(label, topLeft = Offset(x - label.size.width / 2f, iobBottom + BOTTOM_LABEL_GAP.toPx()))
                tick = tick.plusHours(tickIntervalHours)
            }

            // Current time line.
            if (showNowLine && nowMillis in viewportStartMillis..viewportEndMillis) {
                val x = xFor(nowMillis)
                drawLine(
                    color = labelColor.copy(alpha = 0.7f),
                    start = Offset(x, 0f),
                    end = Offset(x, iobBottom),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }

            // Basal strip: 0 U/hr at the top, filled area grows downward (as in Trio).
            val basalSegments = computeBasalSegments(viewportStartMillis, viewportEndMillis, insulinProfile, treatments)
            if (basalSegments.isNotEmpty()) {
                val stripTop = 0f
                val stripBottom = basalStripPx
                val maxRate = basalDomainMaxRate(System.currentTimeMillis(), insulinProfile, treatments)

                fun basalYFor(rate: Double): Float {
                    val fraction = (rate / maxRate).coerceIn(0.0, 1.0)
                    return stripTop + (fraction * (stripBottom - stripTop)).toFloat()
                }

                val fillPath = Path()
                val linePath = Path()
                basalSegments.forEachIndexed { index, segment ->
                    val x1 = xFor(segment.startMillis.coerceIn(viewportStartMillis, viewportEndMillis))
                    val x2 = xFor(segment.endMillis.coerceIn(viewportStartMillis, viewportEndMillis))
                    val y = basalYFor(segment.rateUnitsPerHour)
                    if (index == 0) {
                        fillPath.moveTo(x1, stripTop)
                        fillPath.lineTo(x1, y)
                        linePath.moveTo(x1, y)
                    } else {
                        fillPath.lineTo(x1, y)
                        linePath.lineTo(x1, y)
                    }
                    fillPath.lineTo(x2, y)
                    linePath.lineTo(x2, y)
                }
                val lastEnd = basalSegments.last().endMillis.coerceIn(viewportStartMillis, viewportEndMillis)
                fillPath.lineTo(xFor(lastEnd), stripTop)
                fillPath.close()
                drawPath(fillPath, color = TrioBasal.copy(alpha = 0.3f))
                drawPath(linePath, color = TrioBasal, style = Stroke(width = 1.5.dp.toPx()))
            }

            // Forecast, as in Trio: 5-minute steps from deliverAt, up to 2.5h ahead. Cone is the min..max
            // envelope of all curves; lines draw each curve separately.
            if (forecast != null && forecastDisplay != ForecastDisplay.OFF) {
                val cap = nowMillis + FORECAST_MAX_AHEAD_MILLIS
                fun visible(index: Int): Boolean =
                    forecast.timeMillisAt(index).let { it in viewportStartMillis..viewportEndMillis && it <= cap }
                if (forecastDisplay == ForecastDisplay.CONE) {
                    val shortest = forecast.series.values.minOf { it.size }
                    val count = maxOf(FORECAST_CONE_MIN_POINTS, shortest)
                    val indices = (0 until count).filter { i ->
                        visible(i) && forecast.series.values.any { i < it.size }
                    }
                    if (indices.size >= 2) {
                        fun bound(i: Int, pick: (List<Int>) -> Int) =
                            forecast.series.values.filter { i < it.size }.map { pick(it) }
                        val upper = indices.map { i ->
                            val hi = bound(i) { it[i] }.max()
                            val lo = bound(i) { it[i] }.min()
                            // A zero-width envelope would be invisible, so give it a thin band.
                            Offset(xFor(forecast.timeMillisAt(i)), yFor(if (hi == lo) hi + 1 else hi))
                        }
                        val lower = indices.map { i ->
                            val hi = bound(i) { it[i] }.max()
                            val lo = bound(i) { it[i] }.min()
                            Offset(xFor(forecast.timeMillisAt(i)), yFor(if (hi == lo) lo - 1 else lo))
                        }
                        val cone = Path().apply {
                            moveTo(upper.first().x, upper.first().y)
                            upper.drop(1).forEach { lineTo(it.x, it.y) }
                            lower.asReversed().forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(cone, color = TrioInsulin.copy(alpha = 0.4f))
                    }
                } else {
                    forecast.series.forEach { (type, values) ->
                        val points = values.indices.filter(::visible).map { i ->
                            Offset(xFor(forecast.timeMillisAt(i)), yFor(values[i]))
                        }
                        if (points.size >= 2) {
                            val line = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(line, color = forecastColor(type), style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
            }

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

            sorted.forEach { reading ->
                drawCircle(
                    color = rangeColor(reading.mgDl, alarms, colorScheme),
                    radius = 3.dp.toPx(),
                    center = Offset(xFor(reading.timestamp.toEpochMilli()), yFor(reading.mgDl)),
                )
            }

            // Overrides and temp targets. Temp targets are drawn at their target; overrides have no
            // target in Nightscout, so they get a labeled band near the top instead.
            val adjustments = treatments.filter { treatment ->
                if (!isAdjustmentEventType(treatment.eventType)) return@filter false
                val start = treatment.timestamp.toEpochMilli()
                val end = start + ((treatment.durationMinutes ?: 0.0) * 60_000).toLong()
                end >= viewportStartMillis && start <= viewportEndMillis
            }
            // Overlapping overrides get their own row, so bands and names don't draw over each other.
            val overrideLanes = HashMap<String, Int>()
            val laneEnds = mutableListOf<Long>()
            adjustments
                .filter { !(isTempTargetEventType(it.eventType) && it.targetMgDl != null) }
                .sortedBy { it.timestamp }
                .forEach { override ->
                    val start = override.timestamp.toEpochMilli()
                    val end = start + ((override.durationMinutes ?: 0.0) * 60_000).toLong()
                    val lane = laneEnds.indexOfFirst { it <= start }.let { free ->
                        if (free >= 0) free.also { laneEnds[it] = end } else laneEnds.size.also { laneEnds.add(end) }
                    }
                    overrideLanes[override.id] = lane
                }
            adjustments.forEach { adjustment ->
                val startMillis = adjustment.timestamp.toEpochMilli()
                val endMillis = startMillis + ((adjustment.durationMinutes ?: 0.0) * 60_000).toLong()
                val x1 = xFor(startMillis.coerceIn(viewportStartMillis, viewportEndMillis))
                val x2 = xFor(endMillis.coerceIn(viewportStartMillis, viewportEndMillis))
                val target = adjustment.targetMgDl
                if (isTempTargetEventType(adjustment.eventType) && target != null) {
                    val y = yFor(target.roundToInt())
                    drawLine(
                        color = TrioLoopGreen.copy(alpha = 0.4f),
                        start = Offset(x1, y),
                        end = Offset(x2, y),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                } else {
                    val lane = overrideLanes[adjustment.id] ?: 0
                    val y = glucoseTop + ADJUSTMENT_BAND_TOP_MARGIN.toPx() + lane * ADJUSTMENT_LANE_HEIGHT.toPx()
                    drawLine(
                        color = TrioAccentPurple.copy(alpha = 0.5f),
                        start = Offset(x1, y),
                        end = Offset(x2, y),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    adjustment.notes?.takeIf { it.isNotBlank() }?.let { name ->
                        val label = textMeasurer.measure(
                            text = name,
                            style = TextStyle(fontSize = 9.sp, color = TrioAccentPurple),
                        )
                        drawText(
                            label,
                            topLeft = Offset((x1 + x2) / 2f - label.size.width / 2f, y + 4.dp.toPx()),
                        )
                    }
                }
            }

            // Bolus markers sit just above the BG curve at the time of the dose.
            val boluses = treatments.filter { treatment ->
                val units = treatment.insulinUnits
                units != null && units > 0.0 &&
                    isBolusEventType(treatment.eventType) &&
                    treatment.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis
            }
            boluses.forEach { bolus ->
                val bolusMillis = bolus.timestamp.toEpochMilli()
                val x = xFor(bolusMillis)
                val nearbyMgDl = sorted.minByOrNull { abs(it.timestamp.toEpochMilli() - bolusMillis) }?.mgDl
                val curveY = nearbyMgDl?.let { yFor(it) } ?: (glucoseTop + BOLUS_MARKER_TOP_MARGIN.toPx())
                val apexY = (curveY - 14.dp.toPx()).coerceAtLeast(glucoseTop + BOLUS_MARKER_TOP_MARGIN.toPx())
                val markerPath = Path().apply {
                    moveTo(x - 4.dp.toPx(), apexY - 8.dp.toPx())
                    lineTo(x + 4.dp.toPx(), apexY - 8.dp.toPx())
                    lineTo(x, apexY)
                    close()
                }
                drawPath(markerPath, color = TrioBolus)

                // Small doses keep their marker but lose the amount label.
                if ((bolus.insulinUnits ?: 0.0) >= bolusDisplayThreshold.minUnits) {
                    val amountLabel = textMeasurer.measure(
                        text = formatBolusUnits(bolus.insulinUnits ?: 0.0),
                        style = TextStyle(fontSize = 9.sp, color = TrioBolus),
                    )
                    drawText(
                        amountLabel,
                        topLeft = Offset(x - amountLabel.size.width / 2f, apexY - 8.dp.toPx() - amountLabel.size.height - 2.dp.toPx()),
                    )
                }
            }

            // Carb markers: orange triangle 20 mg/dL below the nearest reading, sized by grams.
            treatments.filter { t ->
                (t.carbsGrams ?: 0.0) > 0.0 && t.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis
            }.forEach { carb ->
                val carbMillis = carb.timestamp.toEpochMilli()
                val x = xFor(carbMillis)
                val grams = carb.carbsGrams ?: 0.0
                val nearbyMgDl = sorted.minByOrNull { abs(it.timestamp.toEpochMilli() - carbMillis) }?.mgDl
                val centerY = nearbyMgDl?.let { yFor(it - 20) } ?: (glucoseBottom - 20.dp.toPx())
                val width = minOf(6.0 + grams * 0.25, 20.0).dp.toPx()
                val height = width * 0.9f
                val gramsLabel = textMeasurer.measure(
                    text = grams.roundToInt().toString(),
                    style = TextStyle(fontSize = 9.sp, color = TrioCob),
                )
                // Keep marker and label inside the glucose area for very low readings.
                val topY = (centerY - height / 2f).coerceAtMost(glucoseBottom - height - gramsLabel.size.height - 2.dp.toPx())
                    .coerceAtLeast(glucoseTop)
                val markerPath = Path().apply {
                    moveTo(x, topY)
                    lineTo(x + width / 2f, topY + height)
                    lineTo(x - width / 2f, topY + height)
                    close()
                }
                drawPath(markerPath, color = TrioCob)
                drawText(gramsLabel, topLeft = Offset(x - gramsLabel.size.width / 2f, topY + height + 1.dp.toPx()))
            }

            // COB curve in the IOB strip, on its own grams scale, drawn under IOB. Nothing is drawn when
            // COB is always zero. Trio's dashed future decay isn't uploaded to Nightscout, so it's not shown.
            val cobPoints = deviceStatusPoints
                .filter { it.cobGrams != null && it.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis }
                .sortedBy { it.timestamp }
            val maxCobGrams = cobPoints.maxOfOrNull { it.cobGrams ?: 0.0 } ?: 0.0
            if (cobPoints.size >= 2 && maxCobGrams > 0.0) {
                val cobScale = maxCobGrams.coerceAtLeast(10.0)
                val fillPath = Path()
                val linePath = Path()
                cobPoints.forEachIndexed { index, point ->
                    val x = xFor(point.timestamp.toEpochMilli())
                    val fraction = ((point.cobGrams ?: 0.0) / cobScale).coerceIn(0.0, 1.0)
                    val y = iobBottom - (fraction * (iobBottom - iobTop)).toFloat()
                    if (index == 0) {
                        fillPath.moveTo(x, iobBottom)
                        fillPath.lineTo(x, y)
                        linePath.moveTo(x, y)
                    } else {
                        fillPath.lineTo(x, y)
                        linePath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(xFor(cobPoints.last().timestamp.toEpochMilli()), iobBottom)
                fillPath.close()
                drawPath(fillPath, color = TrioCob.copy(alpha = 0.2f))
                drawPath(linePath, color = TrioCob, style = Stroke(width = 1.5.dp.toPx()))
            }

            // IOB curve from Trio's own devicestatus. Gaps in the uploads are filled with a dashed
            // estimate computed from bolus decay.
            val iobPoints = deviceStatusPoints
                .filter { it.iobUnits != null && it.timestamp.toEpochMilli() in viewportStartMillis..viewportEndMillis }
                .sortedBy { it.timestamp }
            val diaHours = insulinProfile?.diaHours ?: DEFAULT_DIA_HOURS
            val gapEstimates = findIobGaps(
                viewportStartMillis,
                viewportEndMillis,
                iobPoints.map { it.timestamp.toEpochMilli() },
            ).map { gap ->
                computeIobSeries(gap.first, gap.last, treatments, diaHours, IOB_ESTIMATE_SAMPLE_INTERVAL_MILLIS)
            }.filter { it.isNotEmpty() }

            if (iobPoints.isNotEmpty() || gapEstimates.isNotEmpty()) {
                val maxIob = (
                    iobPoints.map { it.iobUnits!! } + gapEstimates.flatten().map { it.iobUnits }
                    ).maxOrNull()?.coerceAtLeast(0.5) ?: 0.5

                fun iobYFor(units: Double): Float {
                    val fraction = (units / maxIob).coerceIn(0.0, 1.0)
                    return iobBottom - (fraction * (iobBottom - iobTop)).toFloat()
                }

                if (iobPoints.isNotEmpty()) {
                    val fillPath = Path()
                    val linePath = Path()
                    iobPoints.forEachIndexed { index, point ->
                        val x = xFor(point.timestamp.toEpochMilli())
                        val y = iobYFor(point.iobUnits!!)
                        if (index == 0) {
                            fillPath.moveTo(x, iobBottom)
                            fillPath.lineTo(x, y)
                            linePath.moveTo(x, y)
                        } else {
                            fillPath.lineTo(x, y)
                            linePath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(xFor(iobPoints.last().timestamp.toEpochMilli()), iobBottom)
                    fillPath.close()
                    drawPath(fillPath, color = TrioIob.copy(alpha = 0.35f))
                    drawPath(linePath, color = TrioIob, style = Stroke(width = 1.5.dp.toPx()))
                }

                val estimateDash = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                gapEstimates.forEach { series ->
                    val linePath = Path()
                    series.forEachIndexed { index, point ->
                        val x = xFor(point.timestampMillis)
                        val y = iobYFor(point.iobUnits)
                        if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    drawPath(
                        linePath,
                        color = TrioIob.copy(alpha = 0.6f),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = estimateDash),
                    )
                }
            }
        }
    }
}
