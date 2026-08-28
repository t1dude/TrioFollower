package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trionsandroid.app.data.nightscout.Treatment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latest = uiState.readings.firstOrNull()
    val previous = uiState.readings.getOrNull(1)

    // No header/refresh button — pull-to-refresh replaces the button, freeing up vertical space
    // so the chart (and its x-axis labels) sits higher without needing to scroll for it.
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            item {
                val hudState = computePumpCgmHudState(
                    nowMillis = Instant.now().toEpochMilli(),
                    treatments = uiState.treatments,
                    deviceStatusPoints = uiState.deviceStatusPoints,
                    insulinProfile = uiState.insulinProfile,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PumpHudStackLeft(state = hudState, modifier = Modifier.padding(end = 12.dp))
                    GlucoseBubble(
                        latest = latest,
                        previous = previous,
                        unit = uiState.glucoseUnit,
                        alarms = uiState.alarms,
                    )
                    PumpHudStackRight(state = hudState, modifier = Modifier.padding(start = 12.dp))
                }
            }

            if (uiState.readings.isNotEmpty()) {
                item {
                    GlucoseChart(
                        readings = uiState.readings,
                        treatments = uiState.treatments,
                        insulinProfile = uiState.insulinProfile,
                        deviceStatusPoints = uiState.deviceStatusPoints,
                        unit = uiState.glucoseUnit,
                        alarms = uiState.alarms,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                }
            }

            if (uiState.treatments.isNotEmpty()) {
                item {
                    Text(
                        text = "Treatments (last 24h)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
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
                        text = "No data yet. Set your Nightscout URL and token in Settings, then pull to refresh.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
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
