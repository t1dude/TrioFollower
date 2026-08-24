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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioAccentBlue
import com.trionsandroid.app.ui.theme.TrioAccentPurple
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

@Composable
fun GlucoseBubble(
    latest: GlucoseReading?,
    previous: GlucoseReading?,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(168.dp)) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(TrioAccentPurple, TrioAccentBlue, TrioAccentPurple)),
                style = Stroke(width = 6.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(150.dp)
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = unit.format(latest.mgDl),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = " ${latest.trend.arrow}",
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row {
                        Text(
                            text = minutesAgoLabel(latest.timestamp),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (previous != null) {
                            Text(
                                text = "  ${deltaLabel(latest, previous, unit)}",
                                fontSize = 13.sp,
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
