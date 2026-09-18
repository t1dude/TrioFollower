package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                // A tonal surface card (Material 3's recommended way to separate grouped content
                // without heavy shadows) instead of the bubble/pills floating directly on the
                // page background.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    // FlowRow instead of a fixed Row + horizontalScroll: on a screen too narrow
                    // to fit both pill stacks beside the bubble (e.g. a standard phone, vs. the
                    // much wider unfolded screen this was originally tuned against), one stack
                    // wraps onto its own line below instead of being scrolled off-screen and
                    // reading as cropped/cut-off content.
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
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
            }

            if (uiState.readings.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        GlucoseChart(
                            readings = uiState.readings,
                            treatments = uiState.treatments,
                            insulinProfile = uiState.insulinProfile,
                            deviceStatusPoints = uiState.deviceStatusPoints,
                            unit = uiState.glucoseUnit,
                            alarms = uiState.alarms,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
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
