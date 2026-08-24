package com.trionsandroid.app.ui.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioBasal
import com.trionsandroid.app.ui.theme.TrioBolus
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioIob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
private val hourFormatter = DateTimeFormatter.ofPattern("HH")
private val dayFormatter = DateTimeFormatter.ofPattern("dd.MM")

// "Bolus" substring catches Correction/Meal/Snack/Combo Bolus etc. across uploaders, but Trio
// itself (see PumpHistoryStorage.swift's determineBolusEventType) uploads SMB and manually
// administered doses under eventType "SMB" / "External Insulin" specifically — neither contains
// "Bolus", so they need an explicit match alongside the substring heuristic.
private const val BOLUS_EVENT_TYPE_SUBSTRING = "Bolus"
private val EXACT_BOLUS_EVENT_TYPES = setOf("SMB", "External Insulin")

private fun isBolusEventType(eventType: String): Boolean =
    eventType.contains(BOLUS_EVENT_TYPE_SUBSTRING, ignoreCase = true) ||
        EXACT_BOLUS_EVENT_TYPES.any { it.equals(eventType, ignoreCase = true) }

private val BASAL_STRIP_HEIGHT = 40.dp
private val STRIP_TO_GLUCOSE_GAP = 8.dp
private val GLUCOSE_AREA_HEIGHT = 180.dp
private val GLUCOSE_TO_IOB_GAP = 8.dp
private val IOB_STRIP_HEIGHT = 50.dp
private val BOLUS_MARKER_TOP_MARGIN = 10.dp

// A single missed loop cycle (~5min) shouldn't switch that stretch into "estimated" rendering —
// only a real outage should. 20min is 4x Trio's normal cycle.
private const val IOB_GAP_THRESHOLD_MILLIS = 20 * 60_000L
private const val IOB_ESTIMATE_SAMPLE_INTERVAL_MILLIS = 5 * 60_000L

/**
 * Finds stretches of [viewportStartMillis, viewportEndMillis] with no real devicestatus IOB
 * point nearby, so the caller can fill just those with a locally-estimated curve instead of
 * leaving the graph blank across a genuine upload outage.
 */
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

/** "5" for a whole number of units, otherwise trimmed to as few decimals as the dose needs. */
private fun formatBolusUnits(units: Double): String {
    if (units == units.toLong().toDouble()) return units.toLong().toString()
    return String.format(Locale.getDefault(), "%.2f", units).trimEnd('0').trimEnd('.')
}

/**
 * A pannable, pinch-zoomable view of glucose + insulin history, with fling-on-release and a
 * double-tap that cycles through 12h/6h/3h. The visible window is clamped to whatever's actually
 * cached locally (see HomeViewModel's OBSERVE_WINDOW_HOURS) — scrolling back further than that
 * isn't possible yet, since refresh() only actively re-fetches the last 24h; deeper on-demand
 * history fetching is a future enhancement.
 *
 * Three bands top to bottom: basal (0 U/hr at top, growing down), glucose, and IOB (0u at
 * bottom, growing up — plotted from Trio's own devicestatus uploads where available, falling
 * back to a dashed local estimate across genuine upload gaps; see the IOB curve's rendering
 * comment below for why).
 */
@Composable
fun GlucoseChart(
    readings: List<GlucoseReading>,
    treatments: List<Treatment>,
    insulinProfile: InsulinProfile?,
    deviceStatusPoints: List<DeviceStatusPoint>,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val coroutineScope = rememberCoroutineScope()

    // Reserve exactly as much bottom space as the x-axis labels actually need, rather than a
    // guessed constant — different devices/fonts render "00"-style labels at different heights,
    // and two prior fixed-dp guesses in a row both left labels clipped.
    val axisLabelHeightDp = remember(textMeasurer) {
        with(density) {
            textMeasurer.measure("00", style = TextStyle(fontSize = 10.sp)).size.height.toDp()
        }
    }
    val chartHeight = BASAL_STRIP_HEIGHT + STRIP_TO_GLUCOSE_GAP + GLUCOSE_AREA_HEIGHT +
        GLUCOSE_TO_IOB_GAP + IOB_STRIP_HEIGHT + BOTTOM_LABEL_GAP + axisLabelHeightDp + BOTTOM_SAFETY_MARGIN

    val nowMillis = System.currentTimeMillis()
    val dataMinMillis = readings.minOfOrNull { it.timestamp.toEpochMilli() } ?: (nowMillis - DEFAULT_VIEWPORT_MILLIS)
    val dataMaxMillis = nowMillis
    val maxViewportMillis = (dataMaxMillis - dataMinMillis).coerceAtLeast(DEFAULT_VIEWPORT_MILLIS)

    var viewportEndMillis by remember { mutableLongStateOf(dataMaxMillis) }
    var viewportDurationMillis by remember { mutableLongStateOf(DEFAULT_VIEWPORT_MILLIS) }
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var zoomCycleIndex by remember { mutableIntStateOf(-1) }

    val leftGutterPx = with(density) { LEFT_GUTTER.toPx() }

    // dataMinMillis/dataMaxMillis change on essentially every recomposition (dataMaxMillis
    // tracks wall-clock "now"), so they can't be the pointerInput key without restarting the
    // gesture detector mid-gesture. rememberUpdatedState lets the long-lived gesture callback
    // below always read the current value without the detector itself ever restarting.
    val currentDataMinMillis by rememberUpdatedState(dataMinMillis)
    val currentDataMaxMillis by rememberUpdatedState(dataMaxMillis)
    val currentMaxViewportMillis by rememberUpdatedState(maxViewportMillis)

    // Recenters the viewport on targetTimeMillis (kept at targetFraction across the chart width)
    // at the given duration, clamping to the available data range. Shared by live pinch-zoom,
    // double-tap cycling, and (indirectly, via delta shifts) fling — the one place this math lives.
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

            // Vertical time ticks + labels — granularity adapts to how far zoomed out we are.
            // Drawn full-height so they visually tie the basal/glucose/IOB bands together.
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

            // Basal step chart, in the reserved strip above the glucose area. 0 U/hr sits at the
            // top (matching Trio's own layout) and the filled area grows downward as the rate
            // increases, rather than the more conventional "0 at the bottom" bar-chart baseline.
            val basalSegments = computeBasalSegments(viewportStartMillis, viewportEndMillis, insulinProfile, treatments)
            if (basalSegments.isNotEmpty()) {
                val stripTop = 0f
                val stripBottom = basalStripPx
                val maxRate = basalSegments.maxOf { it.rateUnitsPerHour }.coerceAtLeast(0.1)

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

            // Bolus markers, pinned just above wherever the BG curve actually is at that moment
            // (nearest reading by time) rather than a fixed height — so the dose sits right on
            // top of its own result on the curve, matching Trio's placement. Amount is labeled
            // above each marker; no minimum-size filter yet (planned as a Settings threshold).
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

                val amountLabel = textMeasurer.measure(
                    text = formatBolusUnits(bolus.insulinUnits ?: 0.0),
                    style = TextStyle(fontSize = 9.sp, color = TrioBolus),
                )
                drawText(
                    amountLabel,
                    topLeft = Offset(x - amountLabel.size.width / 2f, apexY - 8.dp.toPx() - amountLabel.size.height - 2.dp.toPx()),
                )
            }

            // IOB curve, in the reserved strip below the glucose area. Normal orientation (0u at
            // the bottom, growing up) — unlike basal, Trio doesn't invert this one. Plotted
            // directly from Trio's own devicestatus uploads (the IOB it actually computed and
            // used for dosing — accounting for DIA, peak time, and temp basal netting) rather
            // than a locally-recomputed activity curve, so this always matches what Trio itself
            // shows instead of risking a subtly different number from our own model. Where
            // devicestatus is genuinely missing (loop wasn't uploading), that stretch is instead
            // filled with a dashed, locally-estimated curve from bolus decay — see IobCalculator's
            // doc comment — so the graph doesn't just go blank across an outage.
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
