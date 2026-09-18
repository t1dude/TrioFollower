package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

// The bubble is a fixed 208dp (GlucoseBubble.BUBBLE_CANVAS_SIZE — matches Trio's own sizing
// exactly, not something to shrink to fit). Comfortably fitting it beside two ~95dp-wide pill
// stacks (reservoir/IOB text can run a bit long, e.g. "50+ U") plus their 12dp gaps needs
// roughly 208 + 2*(95+12) ≈ 422dp of card width — rounded up for a little headroom.
private val HUD_SIDE_BY_SIDE_MIN_WIDTH = 430.dp

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
                    // A FlowRow here (tried first) centers each of its own lines independently,
                    // so when the stacks didn't fit beside the bubble it split them apart — one
                    // stayed beside the bubble (shifting it off-center), the other wrapped below
                    // alone. Measuring available width up front instead keeps the bubble always
                    // dead-center: side by side with both stacks when there's room (the original,
                    // Trio-matched layout — fits the wide unfolded screen this was tuned against),
                    // or the bubble alone with both stacks moving together as a pair below it when
                    // there isn't (a standard phone).
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                    ) {
                        val fitsSideBySide = maxWidth >= HUD_SIDE_BY_SIDE_MIN_WIDTH
                        val bubble = @Composable {
                            GlucoseBubble(
                                latest = latest,
                                previous = previous,
                                unit = uiState.glucoseUnit,
                                alarms = uiState.alarms,
                            )
                        }
                        if (fitsSideBySide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PumpHudStackLeft(state = hudState, modifier = Modifier.padding(end = 12.dp))
                                bubble()
                                PumpHudStackRight(state = hudState, modifier = Modifier.padding(start = 12.dp))
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                bubble()
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    PumpHudStackLeft(state = hudState)
                                    PumpHudStackRight(state = hudState)
                                }
                            }
                        }
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
