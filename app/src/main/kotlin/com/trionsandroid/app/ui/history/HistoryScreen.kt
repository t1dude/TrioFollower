package com.trionsandroid.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trionsandroid.app.data.nightscout.GlucoseReading
import com.trionsandroid.app.data.settings.timeFormatter
import com.trionsandroid.app.ui.reasoning.ReasoningSheet

/** History tabs, as in Trio. */
private enum class HistoryMode(val label: String) {
    TREATMENTS("Treatments"),
    MEALS("Meals"),
    GLUCOSE("Glucose"),
    ADJUSTMENTS("Adjustments"),
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(HistoryMode.TREATMENTS) }
    var reasoningReading by remember { mutableStateOf<GlucoseReading?>(null) }
    reasoningReading?.let { reading ->
        ReasoningSheet(
            reading = reading,
            unit = uiState.glucoseUnit,
            timeFormat = uiState.timeFormat,
            load = viewModel::reasoningFor,
            onDismiss = { reasoningReading = null },
        )
    }
    val timeFormatter = remember(uiState.timeFormat) { uiState.timeFormat.timeFormatter() }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HistoryMode.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = HistoryMode.entries.size),
                        // The default checkmark icon adds width and overflows the selected segment; the fill already
                        // shows the selection.
                        icon = {},
                    ) {
                        // Shrinks to fit so "Adjustments" doesn't wrap.
                        Text(
                            text = entry.label,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
                        )
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp),
            ) {
                when (mode) {
                    HistoryMode.TREATMENTS -> treatmentEntries(uiState.treatments, timeFormatter)
                    HistoryMode.MEALS -> mealEntries(uiState.treatments, timeFormatter)
                    HistoryMode.GLUCOSE -> glucoseEntries(
                        uiState.readings,
                        uiState.glucoseUnit,
                        uiState.alarms,
                        uiState.glucoseColorScheme,
                        timeFormatter,
                        onReadingClick = { reasoningReading = it },
                    )
                    HistoryMode.ADJUSTMENTS -> adjustmentEntries(uiState.treatments, uiState.glucoseUnit, timeFormatter)
                }
            }
        }
    }
}
