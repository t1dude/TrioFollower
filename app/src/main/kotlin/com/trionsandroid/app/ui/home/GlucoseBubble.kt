package com.trionsandroid.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.timeFormatter
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioRingGradient
import com.trionsandroid.app.ui.theme.TrioTrendArrowColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

// Sizes match Trio's CurrentGlucoseView.swift CircleShape/TriangleShape exactly (130pt ring, 6pt
// stroke, 35pt triangle offset 85pt from center) at the DEFAULT_BUBBLE_SIZE. BUBBLE_CANVAS_SIZE
// is sized to fully contain the triangle at any rotation (2 * (offset + size/2) = 205dp) without
// clipping it. When [GlucoseBubble] is asked for a different [size] (there's no room for the full
// size beside the HUD pill stacks — see HomeScreen.kt), every one of these is scaled by the same
// factor so the ring/triangle/text proportions stay exactly Trio's, just smaller as a whole.
val DEFAULT_BUBBLE_SIZE = 208.dp
private const val RING_DIAMETER_RATIO = 130f / 208f
private const val RING_STROKE_WIDTH_RATIO = 6f / 208f
private const val TRIANGLE_SIZE_RATIO = 35f / 208f
private const val TRIANGLE_OFFSET_RATIO = 85f / 208f

@Composable
fun GlucoseBubble(
    latest: GlucoseReading?,
    previous: GlucoseReading?,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    timeFormat: TimeFormat = TimeFormat.HOUR_24,
    /** Tapping the bubble (only when there's a reading) — opens the algorithm reasoning. */
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_BUBBLE_SIZE,
) {
    val timeFormatter = remember(timeFormat) { timeFormat.timeFormatter() }
    val ringDiameter = size * RING_DIAMETER_RATIO
    val ringStrokeWidth = size * RING_STROKE_WIDTH_RATIO
    val triangleSize = size * TRIANGLE_SIZE_RATIO
    val triangleOffset = size * TRIANGLE_OFFSET_RATIO
    val scale = size / DEFAULT_BUBBLE_SIZE

    val clickModifier = if (onClick != null && latest != null) Modifier.clip(CircleShape).clickable(onClick = onClick) else Modifier
    Box(modifier = modifier.size(size).then(clickModifier), contentAlignment = Alignment.Center) {
        // Ring + trend triangle are drawn together and rotated as one rigid unit, mirroring
        // Trio's TrendShape(...).rotationEffect(...) — the gradient's "seam" moving with the
        // arrow is intentional, not an artifact.
        Canvas(
            modifier = Modifier
                .size(size)
                .rotate(latest?.trend?.rotationDegrees ?: 0f),
        ) {
            val ringRadiusPx = ringDiameter.toPx() / 2f
            val strokePx = ringStrokeWidth.toPx()
            val trianglePx = triangleSize.toPx()
            val triangleOffsetPx = triangleOffset.toPx()

            // SwiftUI's AngularGradient angle convention: 0° is 3 o'clock (East), positive angles
            // sweep clockwise (confirmed against Apple's docs, not assumed) — the same convention
            // Compose's sweepGradient uses. In that shared frame, Trio's startAngle: 270° is 12
            // o'clock (top), and going to endAngle: -90° (≡270°) sweeps counterclockwise all the
            // way back to top. Compose's sweepGradient always advances clockwise as the color list
            // index increases, so reversing the stop order flips it to match Trio's
            // counterclockwise sweep, and rotating 270° (270° CW moves Compose's East-anchored
            // first stop to top) re-aligns the phase — together they reproduce Trio's exact
            // angle-to-color mapping (verified stop-by-stop: all 5 distinct stops land on the
            // same absolute angle as Trio's).
            rotateDrawScope(degrees = 270f) {
                drawCircle(
                    brush = Brush.sweepGradient(TrioRingGradient.asReversed()),
                    radius = ringRadiusPx,
                    style = Stroke(width = strokePx),
                )
            }

            // Trio's Triangle shape (apex near the top, rounded base) drawn in local coordinates,
            // then rotated 90° (its own fixed rotation, making it point east at rest — i.e. the
            // "Flat" trend) and offset out along +x from center.
            val trianglePath = Path().apply {
                moveTo(trianglePx / 2f, trianglePx * (15f / 35f))
                lineTo(trianglePx, trianglePx)
                quadraticBezierTo(trianglePx / 2f, trianglePx * (27.5f / 35f), 0f, trianglePx)
                close()
            }
            val triangleCenter = Offset(center.x + triangleOffsetPx, center.y)
            translate(left = triangleCenter.x - trianglePx / 2f, top = triangleCenter.y - trianglePx / 2f) {
                rotateDrawScope(degrees = 90f, pivot = Offset(trianglePx / 2f, trianglePx / 2f)) {
                    drawPath(trianglePath, color = TrioTrendArrowColor)
                }
            }
        }

        // The glucose value/delta text is a sibling of the rotating ring, not part of it —
        // matching Trio's ZStack, where only TrendShape carries the rotationEffect.
        Box(
            modifier = Modifier
                .size(ringDiameter - ringStrokeWidth)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (latest == null) {
                Text(
                    text = "--",
                    fontSize = 36.sp * scale,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = unit.format(latest.mgDl),
                        fontSize = 40.sp * scale,
                        fontWeight = FontWeight.Bold,
                        color = rangeColor(latest.mgDl, alarms),
                    )
                    Row {
                        Text(
                            text = minutesAgoLabel(latest.timestamp),
                            fontSize = 13.sp * scale,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (previous != null) {
                            Text(
                                text = "  ${deltaLabel(latest, previous, unit)}",
                                fontSize = 13.sp * scale,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // The reading's own timestamp as reported by Nightscout, not the local fetch time.
                    // Sits in a zero-height slot so it hangs below the minutes-ago/delta line without
                    // adding to the column's height — otherwise the centered value would be pushed up.
                    Box(Modifier.height(0.dp).wrapContentHeight(align = Alignment.Top, unbounded = true)) {
                        Text(
                            text = timeFormatter.format(latest.timestamp.atZone(ZoneId.systemDefault())),
                            fontSize = 11.sp * scale,
                            // Tight line height + upward offset close the gap under the minutes-ago/delta
                            // line, so the text stays inside the circle even on the smallest bubble.
                            lineHeight = 11.sp * scale,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(y = -7.dp * scale),
                        )
                    }
                }
            }
        }
    }
}

private fun minutesAgoLabel(timestamp: Instant): String {
    val minutes = Duration.between(timestamp, Instant.now()).toMinutes().coerceAtLeast(0)
    return "${minutes}m"
}

private fun deltaLabel(latest: GlucoseReading, previous: GlucoseReading, unit: GlucoseUnit): String {
    val deltaMgDl = latest.mgDl - previous.mgDl
    val sign = if (deltaMgDl >= 0) "+" else "-"
    return "$sign${unit.format(abs(deltaMgDl))}"
}
