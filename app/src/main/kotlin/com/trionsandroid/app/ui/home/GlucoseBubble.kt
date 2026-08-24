package com.trionsandroid.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioRingGradient
import com.trionsandroid.app.ui.theme.TrioTrendArrowColor
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

// Sizes match Trio's CurrentGlucoseView.swift CircleShape/TriangleShape exactly (130pt ring, 6pt
// stroke, 35pt triangle offset 85pt from center). BUBBLE_CANVAS_SIZE is sized to fully contain
// the triangle at any rotation (2 * (offset + size/2) = 205dp) without clipping it.
private val RING_DIAMETER = 130.dp
private val RING_STROKE_WIDTH = 6.dp
private val TRIANGLE_SIZE = 35.dp
private val TRIANGLE_OFFSET = 85.dp
private val BUBBLE_CANVAS_SIZE = 208.dp

@Composable
fun GlucoseBubble(
    latest: GlucoseReading?,
    previous: GlucoseReading?,
    unit: GlucoseUnit,
    alarms: AlarmSettings,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(BUBBLE_CANVAS_SIZE), contentAlignment = Alignment.Center) {
        // Ring + trend triangle are drawn together and rotated as one rigid unit, mirroring
        // Trio's TrendShape(...).rotationEffect(...) — the gradient's "seam" moving with the
        // arrow is intentional, not an artifact.
        Canvas(
            modifier = Modifier
                .size(BUBBLE_CANVAS_SIZE)
                .rotate(latest?.trend?.rotationDegrees ?: 0f),
        ) {
            val ringRadiusPx = RING_DIAMETER.toPx() / 2f
            val strokePx = RING_STROKE_WIDTH.toPx()
            val trianglePx = TRIANGLE_SIZE.toPx()
            val triangleOffsetPx = TRIANGLE_OFFSET.toPx()

            drawCircle(
                brush = Brush.sweepGradient(TrioRingGradient),
                radius = ringRadiusPx,
                style = Stroke(width = strokePx),
            )

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
                .size(RING_DIAMETER - RING_STROKE_WIDTH)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            if (latest == null) {
                Text(
                    text = "--",
                    fontSize = 36.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = unit.format(latest.mgDl),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = rangeColor(latest.mgDl, alarms),
                    )
                    Row {
                        Text(
                            text = minutesAgoLabel(latest.timestamp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (previous != null) {
                            Text(
                                text = "  ${deltaLabel(latest, previous, unit)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
