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
import com.trionsandroid.app.ui.permissions.PermissionWarnings
import com.trionsandroid.app.ui.reasoning.ReasoningSheet
import com.trionsandroid.app.data.settings.HomeStatsFace
import java.time.Instant

// Minimum bubble size, for very narrow screens.
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

    // Refresh whenever the app is opened.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    // Also refresh on the user's interval while the app is in the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(uiState.refreshIntervalMinutes, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(uiState.refreshIntervalMinutes.coerceAtLeast(1) * 60_000L)
                viewModel.refresh(userInitiated = false)
            }
        }
    }

    // Pull-to-refresh replaces a refresh button.
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionWarnings(alarmsEnabled = uiState.alarms.alarmsEnabled) }

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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    // The pill stacks keep their natural width beside the bubble. The bubble takes the remaining
                    // width, between MIN_BUBBLE_SIZE and Trio's 208dp.
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
                        PumpHudStackRight(state = hudState, unit = uiState.glucoseUnit, modifier = Modifier.padding(start = 12.dp))
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
                            colorScheme = uiState.glucoseColorScheme,
                            showNowLine = uiState.showNowLine,
                            bolusDisplayThreshold = uiState.bolusDisplayThreshold,
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

            if (uiState.readings.isNotEmpty() && uiState.homeStatsFace != HomeStatsFace.HIDDEN) {
                item {
                    val stats = remember(uiState.readings) { computeDailyStats(uiState.readings) }
                    StatsBar(stats = stats, face = uiState.homeStatsFace, unit = uiState.glucoseUnit)
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
