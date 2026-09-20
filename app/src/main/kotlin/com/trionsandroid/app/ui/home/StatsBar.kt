package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioLoopGreen
import java.util.Locale

// The TIR bar's segment colors as Trio's statsBanner uses them: .red / .orange / .loopGreen /
// .purple (very low / low / in range / high+very high). The system colors are Trio's dark-mode
// values, matching this app's always-dark look.
private val TIR_VERY_LOW = Color(0xFFFF453A)
private val TIR_LOW = Color(0xFFFF9F0A)
private val TIR_IN_RANGE = TrioLoopGreen
private val TIR_HIGH = Color(0xFFBF5AF2)
private val TIR_EMPTY = Color(0x4D9AA3C0)

// Segments thinner than this are left out, and the rest keep proportional widths (Trio: 0.005).
private const val MIN_VISIBLE_FRACTION = 0.005f

/** Home statistics bar below the chart, mirroring Trio's stats banner faces. */
@Composable
fun StatsBar(stats: DailyStats, face: HomeStatsFace, unit: GlucoseUnit, modifier: Modifier = Modifier) {
    if (face == HomeStatsFace.HIDDEN) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (face) {
                HomeStatsFace.TIME_IN_RANGE -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (stats.hasData) "${formatPercent(stats.inRangePct)} %" else "-- %",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Time in Range today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    DistributionBar(stats)
                }
                HomeStatsFace.DISTRIBUTION_BAR -> {
                    Text(
                        text = "Time in Range today",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DistributionBar(stats)
                }
                HomeStatsFace.AVERAGES -> {
                    val mean = stats.meanMgDl
                    val avg = if (mean == null) "--" else "${unit.format(Math.round(mean).toInt())} ${unit.label}"
                    val gmi = stats.gmiPercent?.let { String.format(Locale.getDefault(), "%.1f %%", it) } ?: "--"
                    Text(
                        text = "Avg. Glucose: $avg · GMI $gmi",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Today's Average",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HomeStatsFace.HIDDEN -> Unit
            }
        }
    }
}

private fun formatPercent(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value).removeSuffix(".0").removeSuffix(",0")

/** Rounded segments with small gaps, widths proportional to each range's share of the day. */
@Composable
private fun DistributionBar(stats: DailyStats) {
    val segments: List<Pair<Color, Float>> = if (stats.hasData) {
        listOf(
            TIR_VERY_LOW to (stats.veryLowPct / 100).toFloat(),
            TIR_LOW to (stats.lowPct / 100).toFloat(),
            TIR_IN_RANGE to (stats.inRangePct / 100).toFloat(),
            TIR_HIGH to ((stats.highPct + stats.veryHighPct) / 100).toFloat(),
        )
    } else {
        listOf(TIR_EMPTY to 1f)
    }
    val shown = segments.filter { it.second > MIN_VISIBLE_FRACTION }
    val gap = 2.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val available = maxWidth - gap * (shown.size - 1).coerceAtLeast(0)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            shown.forEach { (color, fraction) ->
                Box(
                    modifier = Modifier
                        .width(available * fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}
