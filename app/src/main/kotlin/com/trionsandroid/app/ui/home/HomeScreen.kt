package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.nightscout.Treatment
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(onClick = viewModel::refresh, enabled = !uiState.isLoading) {
                    Text(if (uiState.isLoading) "Refreshing…" else "Refresh")
                }
            }
        }

        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }

        if (uiState.isLoading && uiState.readings.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (uiState.readings.isNotEmpty()) {
            item {
                Text(
                    text = "Glucose (last 24h)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
            items(uiState.readings, key = { it.id }) { reading ->
                GlucoseRow(reading, uiState.glucoseUnit)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }

        if (uiState.treatments.isNotEmpty()) {
            item {
                Text(
                    text = "Treatments (last 24h)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }
            items(uiState.treatments, key = { it.id }) { treatment ->
                TreatmentRow(treatment)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }

        if (!uiState.isLoading && uiState.readings.isEmpty() && uiState.errorMessage == null) {
            item {
                Text(
                    text = "No data yet. Set your Nightscout URL and token in Settings, then refresh.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun GlucoseRow(reading: GlucoseReading, unit: GlucoseUnit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = timeFormatter.format(reading.timestamp.atZone(ZoneId.systemDefault())),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${unit.format(reading.mgDl)} ${unit.label}",
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = reading.trend.arrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TreatmentRow(treatment: Treatment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = timeFormatter.format(treatment.timestamp.atZone(ZoneId.systemDefault())),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = treatment.eventType,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = buildString {
                treatment.insulinUnits?.let { append("${it}U ") }
                treatment.carbsGrams?.let { append("${it}g ") }
                treatment.basalRateUnitsPerHour?.let { append("${it}U/hr") }
            }.trim(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
