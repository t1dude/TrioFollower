package com.trionsandroid.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trionsandroid.app.ui.reasoning.ReasoningSheet
import java.time.Instant

// Floor so the bubble never shrinks past legibility on a pathologically narrow screen — in
// practice the pill stacks alone are nowhere near wide enough to force this.
private val MIN_BUBBLE_SIZE = 140.dp

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latest = uiState.readings.firstOrNull()
    val previous = uiState.readings.getOrNull(1)
    var showReasoning by remember { mutableStateOf(false) }
    if (showReasoning && latest != null) {
        ReasoningSheet(
            reading = latest,
            unit = uiState.glucoseUnit,
            timeFormat = uiState.timeFormat,
            load = viewModel::reasoningFor,
            onDismiss = { showReasoning = false },
        )
    }

    // Refresh whenever the app is opened (cold start or returning from the background).
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    // And keep refreshing on the user's configured interval while the app stays in the
    // foreground, so new data (and the chart scrolling to it) arrives without any interaction.
    // Restarts when the interval setting changes; suspends entirely while backgrounded (the
    // background sync modes cover that case).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(uiState.refreshIntervalMinutes, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(uiState.refreshIntervalMinutes.coerceAtLeast(1) * 60_000L)
                viewModel.refresh(userInitiated = false)
            }
        }
    }

    // No header/refresh button — pull-to-refresh replaces the button, freeing up vertical space
    // so the chart (and its x-axis labels) sits higher without needing to scroll for it.
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.refresh() },
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
                    // Two width-threshold-guessing attempts before this one both misjudged
                    // available width (one relied on a hidden horizontalScroll that read as
                    // cropped content; the next used a hardcoded Dp threshold that made the wrong
                    // call even on a wide unfolded screen). This drops estimating entirely: the
                    // pill stacks always keep their natural width and always stay beside the
                    // bubble, and the bubble's container gets weight(1f) — whatever width is
                    // actually left over after the stacks, measured for real via
                    // BoxWithConstraints — clamped to Trio's original 208dp as a ceiling (so it
                    // doesn't balloon on a very wide screen) and MIN_BUBBLE_SIZE as a legibility
                    // floor. The ring/triangle/text inside GlucoseBubble scale together with it.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PumpHudStackLeft(state = hudState, modifier = Modifier.padding(end = 12.dp))
                        Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                            BoxWithConstraints {
                                GlucoseBubble(
                                    latest = latest,
                                    previous = previous,
                                    unit = uiState.glucoseUnit,
                                    alarms = uiState.alarms,
                                    timeFormat = uiState.timeFormat,
                                    onClick = { showReasoning = true },
                                    size = maxWidth.coerceIn(MIN_BUBBLE_SIZE, DEFAULT_BUBBLE_SIZE),
                                )
                            }
                        }
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
                            timeFormat = uiState.timeFormat,
                            forecast = uiState.forecast,
                            forecastDisplay = uiState.forecastDisplay,
                            scrollToLatestKey = uiState.refreshCount,
                            forceScrollToLatest = uiState.forceScrollToLatest,
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
