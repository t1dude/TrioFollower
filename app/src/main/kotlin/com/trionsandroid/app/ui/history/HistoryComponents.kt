package com.trionsandroid.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.nightscout.isAdjustmentEventType
import com.trionsandroid.app.data.nightscout.isBolusEventType
import com.trionsandroid.app.data.nightscout.isExternalInsulinEventType
import com.trionsandroid.app.data.nightscout.isOverrideEventType
import com.trionsandroid.app.data.nightscout.isSmbEventType
import com.trionsandroid.app.data.nightscout.isTempBasalEventType
import com.trionsandroid.app.data.settings.AlarmSettings
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.ui.home.rangeColor
import com.trionsandroid.app.ui.theme.TrioAccentPurple
import com.trionsandroid.app.ui.theme.TrioCarb
import com.trionsandroid.app.ui.theme.TrioInsulin
import com.trionsandroid.app.ui.theme.TrioLoopGreen
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Includes the date, not just the time (unlike Trio's own history rows) — our History tab
// observes a 30-day local cache, not Trio's ~24h-scoped fetches, so a bare time-of-day would be
// ambiguous for older entries.
private val timeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

/** Every History row shares this shape: a colored dot, a primary label, a secondary value, and a
 *  timestamp — mirrors Trio's own HistoryRootView row layout (dot + label + value + time). */
@Composable
private fun HistoryEntryRow(dotColor: Color, label: String, value: String, timestamp: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        if (value.isNotEmpty()) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Text(
            text = timestamp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EmptyHistoryMessage(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

private fun formatTimestamp(treatment: Treatment) =
    timeFormatter.format(treatment.timestamp.atZone(ZoneId.systemDefault()))

private fun formatTimestamp(reading: GlucoseReading) =
    timeFormatter.format(reading.timestamp.atZone(ZoneId.systemDefault()))

/** All insulin delivery — basals (temp basal treatments), bolus, SMB, and external doses —
 *  matching Trio's own treatmentsList categories (HistoryRootView+Treatments.swift). */
fun LazyListScope.treatmentEntries(treatments: List<Treatment>) {
    val insulinTreatments = treatments.filter { isBolusEventType(it.eventType) || isTempBasalEventType(it.eventType) }
    if (insulinTreatments.isEmpty()) {
        item { EmptyHistoryMessage("No insulin history yet.") }
        return
    }
    items(insulinTreatments, key = { it.id }) { treatment ->
        val (dotColor, label, value) = treatmentDisplay(treatment)
        HistoryEntryRow(dotColor, label, value, formatTimestamp(treatment))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

private fun treatmentDisplay(treatment: Treatment): Triple<Color, String, String> {
    val eventType = treatment.eventType
    return when {
        isSmbEventType(eventType) ->
            Triple(TrioInsulin, "SMB", "%.2f U".format(treatment.insulinUnits ?: 0.0))
        isExternalInsulinEventType(eventType) ->
            Triple(TrioInsulin, "External", "%.2f U".format(treatment.insulinUnits ?: 0.0))
        isBolusEventType(eventType) ->
            Triple(TrioInsulin, eventType, "%.2f U".format(treatment.insulinUnits ?: 0.0))
        else -> { // isTempBasalEventType
            val durationSuffix = treatment.durationMinutes
                ?.takeIf { it > 0 }
                ?.let { " (${it.toInt()} min)" }
                .orEmpty()
            Triple(
                TrioInsulin.copy(alpha = 0.4f),
                "Temp Basal",
                "%.2f U/hr$durationSuffix".format(treatment.basalRateUnitsPerHour ?: 0.0),
            )
        }
    }
}

/** All carb entries. */
fun LazyListScope.mealEntries(treatments: List<Treatment>) {
    val meals = treatments.filter { (it.carbsGrams ?: 0.0) > 0.0 }
    if (meals.isEmpty()) {
        item { EmptyHistoryMessage("No carb entries yet.") }
        return
    }
    items(meals, key = { it.id }) { treatment ->
        HistoryEntryRow(
            dotColor = TrioCarb,
            label = "Carbs",
            value = "%.0f g".format(treatment.carbsGrams ?: 0.0),
            timestamp = formatTimestamp(treatment),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

/** Every cached glucose reading, colored by range like the rest of the app. */
fun LazyListScope.glucoseEntries(readings: List<GlucoseReading>, unit: GlucoseUnit, alarms: AlarmSettings) {
    if (readings.isEmpty()) {
        item { EmptyHistoryMessage("No glucose readings yet.") }
        return
    }
    items(readings, key = { it.id }) { reading ->
        HistoryEntryRow(
            dotColor = rangeColor(reading.mgDl, alarms),
            label = "${unit.format(reading.mgDl)} ${unit.label}",
            value = reading.trend.arrow,
            timestamp = formatTimestamp(reading),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

/** Overrides and temp targets, matching Trio's combined Adjustments list
 *  (HistoryRootView+Adjustments.swift): dot + name + target (if any) + a start-end range instead
 *  of a single timestamp, since an adjustment spans a duration rather than being instantaneous. */
fun LazyListScope.adjustmentEntries(treatments: List<Treatment>, unit: GlucoseUnit) {
    val adjustments = treatments.filter { isAdjustmentEventType(it.eventType) }
    if (adjustments.isEmpty()) {
        item { EmptyHistoryMessage("No adjustments yet.") }
        return
    }
    items(adjustments, key = { it.id }) { treatment ->
        val isOverride = isOverrideEventType(treatment.eventType)
        val name = treatment.notes?.takeIf { it.isNotBlank() } ?: if (isOverride) "Override" else "Temp Target"
        val value = treatment.targetMgDl?.let { "${unit.format(it.roundToInt())} ${unit.label}" }.orEmpty()
        val endInstant = treatment.timestamp.plusSeconds(((treatment.durationMinutes ?: 0.0) * 60).toLong())
        val range = "${timeFormatter.format(treatment.timestamp.atZone(ZoneId.systemDefault()))} – " +
            timeFormatter.format(endInstant.atZone(ZoneId.systemDefault()))
        HistoryEntryRow(
            dotColor = if (isOverride) TrioAccentPurple else TrioLoopGreen,
            label = name,
            value = value,
            timestamp = range,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}
