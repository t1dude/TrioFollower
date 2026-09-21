package com.trionsandroid.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.trionsandroid.app.data.nightscout.Forecast
import com.trionsandroid.app.data.nightscout.ForecastType
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.hourFormatter
import com.trionsandroid.app.data.settings.timeFormatter
import com.trionsandroid.app.ui.home.rangeColor
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioInsulin
import com.trionsandroid.app.ui.theme.TrioRingGradient
import com.trionsandroid.app.ui.theme.TrioTrendArrowColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Everything a widget needs to draw itself. Readings are newest last. */
data class WidgetData(
    val readings: List<GlucoseReading>,
    val forecast: Forecast?,
    val unit: GlucoseUnit,
    val timeFormat: TimeFormat,
    val alarms: AlarmSettings,
    val colorScheme: GlucoseColorScheme,
    val forecastDisplay: ForecastDisplay,
    val showNowLine: Boolean,
    /** 0 = solid black background, 100 = fully transparent. */
    val transparencyPercent: Int,
    val nowMillis: Long,
)

private const val HOUR_MILLIS = 3_600_000L
private const val PAST_MILLIS = 3 * HOUR_MILLIS
private const val FUTURE_MILLIS = 2 * HOUR_MILLIS
private const val FORECAST_MAX_AHEAD_MILLIS = 150 * 60_000L
private const val FORECAST_CONE_MIN_POINTS = 12
private val WHITE = Color.WHITE
private val MUTED = Color.argb(200, 200, 205, 220)

/** Draws the widgets into bitmaps. Android Canvas is used because RemoteViews can't host Compose. */
object WidgetRenderer {

    fun renderPreview(data: WidgetData, kind: WidgetKind, widthPx: Int, heightPx: Int, density: Float): Bitmap =
        if (kind == WidgetKind.BUBBLE) renderBubble(data, widthPx, heightPx, density) else renderGraph(data, widthPx, heightPx, density)

    fun renderBubble(data: WidgetData, widthPx: Int, heightPx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // No square background: the background is only the inside of the glucose circle.
        val size = min(widthPx, heightPx) - 8 * density
        drawBubble(canvas, widthPx / 2f, heightPx / 2f, size, data, fillCircle = true)
        return bitmap
    }

    fun renderGraph(data: WidgetData, widthPx: Int, heightPx: Int, density: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, widthPx, heightPx, density, data.transparencyPercent)
        val pad = 6 * density
        val bubbleSize = min(heightPx - 2 * pad, widthPx * 0.38f)
        drawBubble(canvas, pad + bubbleSize / 2f, heightPx / 2f, bubbleSize, data)
        val graphRect = RectF(pad * 2 + bubbleSize, pad, widthPx - pad, heightPx - pad)
        drawGraph(canvas, graphRect, data, density)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, w: Int, h: Int, density: Float, transparencyPercent: Int) {
        val alpha = ((100 - transparencyPercent.coerceIn(0, 100)) / 100f * 255).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha, 0, 0, 0) }
        val radius = 20 * density
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
    }

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx
        textAlign = Paint.Align.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setShadowLayer(sizePx * 0.15f, 0f, 0f, Color.BLACK)
    }

    /** Same ring, trend arrow and text as the app's bubble, sized as a fraction of [size]. */
    private fun drawBubble(canvas: Canvas, cx: Float, cy: Float, size: Float, data: WidgetData, fillCircle: Boolean = false) {
        val latest = data.readings.lastOrNull()
        val previous = data.readings.getOrNull(data.readings.size - 2)
        val u = size / 208f

        if (fillCircle) {
            val alpha = ((100 - data.transparencyPercent.coerceIn(0, 100)) / 100f * 255).toInt()
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha, 0, 0, 0) }
            canvas.drawCircle(cx, cy, 130 * u / 2f - 3 * u, fill)
        }

        canvas.save()
        canvas.rotate(latest?.trend?.rotationDegrees ?: 0f, cx, cy)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6 * u
            shader = SweepGradient(cx, cy, TrioRingGradient.asReversed().map { it.toArgb() }.toIntArray(), null).apply {
                setLocalMatrix(Matrix().apply { postRotate(270f, cx, cy) })
            }
        }
        canvas.drawCircle(cx, cy, 130 * u / 2f, ringPaint)

        val tri = 35 * u
        val trianglePath = Path().apply {
            moveTo(tri / 2f, tri * 15f / 35f)
            lineTo(tri, tri)
            quadTo(tri / 2f, tri * 27.5f / 35f, 0f, tri)
            close()
        }
        canvas.save()
        canvas.translate(cx + 85 * u - tri / 2f, cy - tri / 2f)
        canvas.rotate(90f, tri / 2f, tri / 2f)
        canvas.drawPath(trianglePath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TrioTrendArrowColor.toArgb() })
        canvas.restore()
        canvas.restore()

        if (latest == null) {
            canvas.drawText("--", cx, cy + 12 * u, textPaint(36 * u, MUTED, false))
            return
        }
        val top = cy - 32 * u
        canvas.drawText(data.unit.format(latest.mgDl), cx, top + 38 * u, textPaint(40 * u, WHITE, true))

        val minutes = Duration.between(latest.timestamp, Instant.ofEpochMilli(data.nowMillis)).toMinutes().coerceAtLeast(0)
        val delta = previous?.let {
            val d = latest.mgDl - it.mgDl
            "  " + (if (d >= 0) "+" else "-") + data.unit.format(abs(d))
        }.orEmpty()
        canvas.drawText("${minutes}m$delta", cx, top + 60 * u, textPaint(13 * u, MUTED, true))

        val time = data.timeFormat.timeFormatter().format(latest.timestamp.atZone(ZoneId.systemDefault()))
        canvas.drawText(time, cx, top + 72 * u, textPaint(11 * u, MUTED, false))
    }

    private fun drawGraph(canvas: Canvas, rect: RectF, data: WidgetData, density: Float) {
        val start = data.nowMillis - PAST_MILLIS
        val end = data.nowMillis + FUTURE_MILLIS
        val labelHeight = 12 * density
        val plot = RectF(rect.left, rect.top, rect.right, rect.bottom - labelHeight)

        val readings = data.readings.filter { it.timestamp.toEpochMilli() in start..data.nowMillis }
        val forecast = if (data.forecastDisplay == ForecastDisplay.OFF) null else data.forecast
        val cap = data.nowMillis + FORECAST_MAX_AHEAD_MILLIS
        val forecastValues = forecast?.let { f ->
            f.series.values.flatMap { values ->
                values.filterIndexed { i, _ -> f.timeMillisAt(i) in start..min(end, cap) }
            }
        }.orEmpty()

        val all = readings.map { it.mgDl } + forecastValues
        val lo = (min(all.minOrNull() ?: 70, data.alarms.low.thresholdMgDl) - 10).coerceAtLeast(40)
        val hi = (max(all.maxOrNull() ?: 180, data.alarms.high.thresholdMgDl) + 10).coerceAtMost(300)

        fun x(millis: Long) = plot.left + (millis - start).toFloat() / (end - start) * plot.width()
        fun y(mgDl: Int) = plot.bottom - (mgDl.coerceIn(lo, hi) - lo).toFloat() / (hi - lo) * plot.height()

        // Hour grid and labels
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(45, 255, 255, 255); strokeWidth = density }
        val labelPaint = textPaint(9 * density, MUTED, false).apply { clearShadowLayer() }
        val zone = ZoneId.systemDefault()
        val hourFmt = data.timeFormat.hourFormatter()
        var tick = Instant.ofEpochMilli(start).atZone(zone).withMinute(0).withSecond(0).withNano(0)
        if (tick.toInstant().toEpochMilli() < start) tick = tick.plusHours(1)
        while (tick.toInstant().toEpochMilli() <= end) {
            val tx = x(tick.toInstant().toEpochMilli())
            canvas.drawLine(tx, plot.top, tx, plot.bottom, gridPaint)
            canvas.drawText(hourFmt.format(tick), tx, rect.bottom - 1 * density, labelPaint)
            tick = tick.plusHours(1)
        }

        // Alarm thresholds
        val dash = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        if (data.alarms.low.enabled) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TrioGlucoseLow.toArgb(); strokeWidth = 1.2f * density; pathEffect = dash
            }
            canvas.drawLine(plot.left, y(data.alarms.low.thresholdMgDl), plot.right, y(data.alarms.low.thresholdMgDl), paint)
        }
        if (data.alarms.high.enabled) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TrioGlucoseHigh.toArgb(); strokeWidth = 1.2f * density; pathEffect = dash
            }
            canvas.drawLine(plot.left, y(data.alarms.high.thresholdMgDl), plot.right, y(data.alarms.high.thresholdMgDl), paint)
        }

        if (data.showNowLine) {
            val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); strokeWidth = 1.5f * density }
            canvas.drawLine(x(data.nowMillis), plot.top, x(data.nowMillis), plot.bottom, nowPaint)
        }

        if (forecast != null) drawForecast(canvas, forecast, data.forecastDisplay, data.nowMillis, start, end, ::x, ::y, density)

        // Readings
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 200, 205, 220); strokeWidth = 1.5f * density }
        readings.zipWithNext { a, b ->
            canvas.drawLine(x(a.timestamp.toEpochMilli()), y(a.mgDl), x(b.timestamp.toEpochMilli()), y(b.mgDl), linePaint)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        readings.forEach { r ->
            dotPaint.color = rangeColor(r.mgDl, data.alarms, data.colorScheme).toArgb()
            canvas.drawCircle(x(r.timestamp.toEpochMilli()), y(r.mgDl), 2.2f * density, dotPaint)
        }
    }

    private fun drawForecast(
        canvas: Canvas,
        forecast: Forecast,
        display: ForecastDisplay,
        nowMillis: Long,
        start: Long,
        end: Long,
        x: (Long) -> Float,
        y: (Int) -> Float,
        density: Float,
    ) {
        val cap = nowMillis + FORECAST_MAX_AHEAD_MILLIS
        fun visible(i: Int) = forecast.timeMillisAt(i).let { it in start..end && it <= cap }
        if (display == ForecastDisplay.CONE) {
            val count = max(FORECAST_CONE_MIN_POINTS, forecast.series.values.minOf { it.size })
            val indices = (0 until count).filter { i -> visible(i) && forecast.series.values.any { i < it.size } }
            if (indices.size < 2) return
            fun bounds(i: Int): Pair<Int, Int> {
                val values = forecast.series.values.filter { i < it.size }.map { it[i] }
                val hi = values.max()
                val lo = values.min()
                return if (hi == lo) (hi + 1) to (lo - 1) else hi to lo
            }
            val path = Path()
            indices.forEachIndexed { n, i ->
                val px = x(forecast.timeMillisAt(i))
                if (n == 0) path.moveTo(px, y(bounds(i).first)) else path.lineTo(px, y(bounds(i).first))
            }
            indices.asReversed().forEach { i -> path.lineTo(x(forecast.timeMillisAt(i)), y(bounds(i).second)) }
            path.close()
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TrioInsulin.copy(alpha = 0.4f).toArgb() })
        } else {
            forecast.series.forEach { (type, values) ->
                val points = values.indices.filter(::visible)
                if (points.size < 2) return@forEach
                val path = Path()
                points.forEachIndexed { n, i ->
                    val px = x(forecast.timeMillisAt(i))
                    if (n == 0) path.moveTo(px, y(values[i])) else path.lineTo(px, y(values[i]))
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.8f * density; color = forecastColor(type)
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    // Trio's forecast line colors (see GlucoseChart).
    private fun forecastColor(type: ForecastType): Int = when (type) {
        ForecastType.IOB -> TrioInsulin.toArgb()
        ForecastType.ZT -> 0xFF7161EF.toInt()
        ForecastType.COB -> 0xFFFF9500.toInt()
        ForecastType.UAM -> 0xFFD12BF7.toInt()
    }
}
