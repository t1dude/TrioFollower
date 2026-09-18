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

/** Mirrors Trio's History.Mode (HistoryDataFlow.swift): Treatments / Glucose / Meals /
 *  Adjustments, picked with a segmented control above the list. */
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
                        // SegmentedButton measures its default checkmark icon and the label
                        // independently, then adds their widths together — so a label auto-sized
                        // to fill the segment (see below) plus the icon on top of that overflows
                        // past the segment's actual bounds on the selected tab specifically. The
                        // active/inactive background fill already shows which tab is selected, so
                        // the icon is redundant — dropping it removes the extra width entirely.
                        icon = {},
                    ) {
                        // Shrinks to fit its own segment's width on narrower/smaller-scale
                        // displays instead of wrapping "Adjustments" onto a second line.
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            ) {
                when (mode) {
                    HistoryMode.TREATMENTS -> treatmentEntries(uiState.treatments)
                    HistoryMode.MEALS -> mealEntries(uiState.treatments)
                    HistoryMode.GLUCOSE -> glucoseEntries(uiState.readings, uiState.glucoseUnit, uiState.alarms)
                    HistoryMode.ADJUSTMENTS -> adjustmentEntries(uiState.treatments, uiState.glucoseUnit)
                }
            }
        }
    }
}
