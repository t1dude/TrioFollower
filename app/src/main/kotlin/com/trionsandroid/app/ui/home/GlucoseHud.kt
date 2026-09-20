package com.trionsandroid.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.theme.TrioCob
import kotlin.math.roundToInt
import com.trionsandroid.app.data.nightscout.DeviceStatusPoint
import com.trionsandroid.app.data.nightscout.InsulinProfile
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.ui.theme.TrioInsulin
import com.trionsandroid.app.ui.theme.TrioLoopGreen
import com.trionsandroid.app.ui.theme.TrioLoopRed
import com.trionsandroid.app.ui.theme.TrioOnSurfaceMuted
import com.trionsandroid.app.ui.theme.TrioWarningOrange
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

// Assumed lifetimes, since Nightscout has no pump or sensor expiry field: 3 days for a site,
// 10 days for a CGM sensor.
private const val SITE_CHANGE_INTERVAL_DAYS = 3L
private const val SENSOR_DURATION_DAYS = 10L

private const val SITE_CHANGE_EVENT_TYPE = "Site Change"
private const val SENSOR_START_EVENT_TYPE = "Sensor Start"

private const val LEGEND_HANG_MILLIS = 1500L

data class PumpCgmHudState(
    val currentIobUnits: Double?,
    // 0 when nothing is on board or carbs are never logged; null when there is no recent data.
    val currentCobGrams: Double?,
    val eventualBgMgDl: Int?,
    val reservoirUnits: Double?,
    val siteRemaining: Duration?,
    val sensorRemaining: Duration?,
)

/** Derives the HUD values from data already cached locally. */
fun computePumpCgmHudState(
    nowMillis: Long,
    treatments: List<Treatment>,
    deviceStatusPoints: List<DeviceStatusPoint>,
    insulinProfile: InsulinProfile?,
): PumpCgmHudState {
    val latestDeviceStatus = deviceStatusPoints.maxByOrNull { it.timestamp.toEpochMilli() }
    val latestIsFresh = latestDeviceStatus != null &&
        nowMillis - latestDeviceStatus.timestamp.toEpochMilli() <= IOB_GAP_THRESHOLD_MILLIS
    val currentIobUnits = if (latestIsFresh && latestDeviceStatus != null) {
        latestDeviceStatus.iobUnits
    } else {
        val diaHours = insulinProfile?.diaHours ?: DEFAULT_DIA_HOURS
        computeIobSeries(nowMillis - 60_000L, nowMillis, treatments, diaHours, 60_000L).lastOrNull()?.iobUnits
    }

    val reservoirUnits = deviceStatusPoints
        .filter { it.reservoirUnits != null }
        .maxByOrNull { it.timestamp.toEpochMilli() }
        ?.reservoirUnits

    val siteChangedAt = treatments
        .filter { it.eventType == SITE_CHANGE_EVENT_TYPE }
        .maxByOrNull { it.timestamp.toEpochMilli() }
        ?.timestamp
    val sensorStartedAt = treatments
        .filter { it.eventType == SENSOR_START_EVENT_TYPE }
        .maxByOrNull { it.timestamp.toEpochMilli() }
        ?.timestamp

    val now = Instant.ofEpochMilli(nowMillis)
    val siteRemaining = siteChangedAt?.let { Duration.ofDays(SITE_CHANGE_INTERVAL_DAYS) - Duration.between(it, now) }
    val sensorRemaining = sensorStartedAt?.let { Duration.ofDays(SENSOR_DURATION_DAYS) - Duration.between(it, now) }

    // COB and the eventual prediction go blank when the latest status is old.
    val currentCobGrams = if (latestIsFresh) latestDeviceStatus?.cobGrams else null
    val eventualBgMgDl = if (latestIsFresh) latestDeviceStatus?.eventualBgMgDl else null

    return PumpCgmHudState(currentIobUnits, currentCobGrams, eventualBgMgDl, reservoirUnits, siteRemaining, sensorRemaining)
}

/** As Trio's PumpView: red under 8h left, orange under a day, green beyond. */
private fun remainingTimeColor(remaining: Duration?): Color = when {
    remaining == null -> TrioOnSurfaceMuted
    remaining <= Duration.ofHours(8) -> TrioLoopRed
    remaining <= Duration.ofDays(1) -> TrioWarningOrange
    else -> TrioLoopGreen
}

/** As Trio's PumpView reservoir thresholds. */
private fun reservoirColor(units: Double?): Color = when {
    units == null -> TrioOnSurfaceMuted
    units.isInfinite() -> TrioInsulin
    units <= 10.0 -> TrioLoopRed
    units <= 30.0 -> TrioWarningOrange
    else -> TrioInsulin
}

/** "2d 4h", "6h 30m", "45m", or "Replace" once expired. */
private fun formatRemaining(remaining: Duration): String {
    if (remaining.isNegative || remaining.isZero) return "Replace"
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    val minutes = remaining.toMinutes() % 60
    return when {
        days >= 1 -> "${days}d ${hours}h"
        hours >= 1 -> if (hours < 12) "${hours}h ${minutes}m" else "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatUnits(units: Double?): String = when {
    units == null -> "--"
    units.isInfinite() -> "50+"
    else -> String.format(Locale.getDefault(), "%.1f", units)
}

private val HUD_PILL_SPACING = 6.dp

/** Reservoir, IOB and COB pills. The middle one (IOB) sits on the bubble's center line. */
@Composable
fun PumpHudStackLeft(state: PumpCgmHudState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HUD_PILL_SPACING), horizontalAlignment = Alignment.CenterHorizontally) {
        HudPill(
            icon = Icons.Filled.Medication,
            label = "${formatUnits(state.reservoirUnits)} U",
            legend = "Reservoir",
            color = reservoirColor(state.reservoirUnits),
        )
        HudPill(
            icon = Icons.Filled.Vaccines,
            label = "${formatUnits(state.currentIobUnits)} U",
            legend = "Insulin on board",
            color = if (state.currentIobUnits != null) TrioInsulin else TrioOnSurfaceMuted,
        )
        HudPill(
            icon = Icons.Filled.Restaurant,
            label = state.currentCobGrams?.let { "${it.roundToInt()} g" } ?: "-- g",
            legend = "Carbs on board",
            color = if (state.currentCobGrams != null) TrioCob else TrioOnSurfaceMuted,
        )
    }
}

/** Sensor, pump-site and eventual-glucose pills. The middle one sits on the bubble's center line. */
@Composable
fun PumpHudStackRight(state: PumpCgmHudState, unit: GlucoseUnit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(HUD_PILL_SPACING), horizontalAlignment = Alignment.CenterHorizontally) {
        HudPill(
            icon = Icons.Filled.Sensors,
            label = state.sensorRemaining?.let(::formatRemaining) ?: "--",
            legend = "Sensor time left",
            color = remainingTimeColor(state.sensorRemaining),
        )
        HudPill(
            icon = Icons.Filled.HourglassBottom,
            label = state.siteRemaining?.let(::formatRemaining) ?: "--",
            legend = "Pump site time left",
            color = remainingTimeColor(state.siteRemaining),
        )
        // Eventual glucose, as Trio shows next to its bubble. It's the loop's own eventualBG.
        HudPill(
            icon = Icons.Filled.ArrowCircleRight,
            label = state.eventualBgMgDl?.let { unit.format(it) } ?: "--",
            legend = "Eventual glucose",
            color = if (state.eventualBgMgDl != null) MaterialTheme.colorScheme.onSurface else TrioOnSurfaceMuted,
        )
    }
}

/** Tapping a pill shows its meaning for ~1.5s. */
@Composable
private fun HudPill(icon: ImageVector, label: String, legend: String, color: Color) {
    var showLegend by remember { mutableStateOf(false) }
    LaunchedEffect(showLegend) {
        if (showLegend) {
            delay(LEGEND_HANG_MILLIS)
            showLegend = false
        }
    }

    val legendAlpha by animateFloatAsState(if (showLegend) 1f else 0f, label = "legendAlpha")

    // The legend takes no space, so it can't change the stack's height or width (which would resize the bubble).
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(if (showLegend) 1f else 0f)) {
        OutlinedCard(
            onClick = { showLegend = true },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.5.dp, color.copy(alpha = 0.4f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(icon, contentDescription = legend, tint = color, modifier = Modifier.size(16.dp))
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Box(Modifier.size(0.dp).wrapContentSize(align = Alignment.TopCenter, unbounded = true)) {
            // Animated alpha instead of AnimatedVisibility, which resolves to Column's scoped overload here.
            if (legendAlpha > 0f) {
                Text(
                    text = legend,
                    color = color,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .alpha(legendAlpha)
                        .padding(top = 2.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}
