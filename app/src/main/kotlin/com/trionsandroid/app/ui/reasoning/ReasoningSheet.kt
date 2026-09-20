package com.trionsandroid.app.ui.reasoning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.Reasoning
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.timeFormatter
import java.time.ZoneId

/**
 * Shows the loop's algorithm reasoning for one glucose reading — used by both the Home bubble
 * (latest reading) and History's glucose rows (the tapped reading). [load] looks up the reasoning
 * for the reading; the sheet shows a spinner until it returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningSheet(
    reading: GlucoseReading,
    unit: GlucoseUnit,
    timeFormat: TimeFormat,
    load: suspend (GlucoseReading) -> Reasoning?,
    onDismiss: () -> Unit,
) {
    val formatter = remember(timeFormat) { timeFormat.timeFormatter() }
    var loaded by remember(reading.id) { mutableStateOf(false) }
    var reasoning by remember(reading.id) { mutableStateOf<Reasoning?>(null) }
    LaunchedEffect(reading.id) {
        reasoning = load(reading)
        loaded = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Algorithm reasoning", style = MaterialTheme.typography.titleLarge)
            Text(
                "${unit.format(reading.mgDl)} ${unit.label} at " +
                    formatter.format(reading.timestamp.atZone(ZoneId.systemDefault())),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !loaded -> CircularProgressIndicator()
                reasoning == null -> Text(
                    "No algorithm reasoning found for this reading.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    SelectionContainer { Text(reasoning!!.text) }
                    Text(
                        "Loop run at " + formatter.format(reasoning!!.timestamp.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
