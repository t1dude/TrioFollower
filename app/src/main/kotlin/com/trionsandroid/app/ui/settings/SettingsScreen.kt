package com.trionsandroid.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
import com.trionsandroid.app.data.settings.NO_DATA_MINUTES_OPTIONS
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.allowedRefreshIntervals
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioGlucoseUrgent
import com.trionsandroid.app.data.settings.BackgroundMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(expandBasicSettings: Boolean = false, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }
    var showPredictedHighInfo by remember { mutableStateOf(false) }
    if (showPredictedHighInfo) PredictedHighInfoSheet(onDismiss = { showPredictedHighInfo = false })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item {
            SettingsSection(title = "Basic Settings", initiallyExpanded = expandBasicSettings) {
                Text(
                    text = "Nightscout",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.nightscoutUrl,
                    onValueChange = viewModel::onNightscoutUrlChange,
                    label = { Text("Nightscout URL") },
                    placeholder = { Text("https://my-site.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.accessToken,
                    onValueChange = viewModel::onAccessTokenChange,
                    label = { Text("Access token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth()) {
                    Text("Test connection")
                }
                ConnectionStatusRow(uiState.connectionTestState)

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Units",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GlucoseUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = uiState.glucoseUnit == unit,
                            onClick = { viewModel.onGlucoseUnitChange(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = GlucoseUnit.entries.size),
                        ) {
                            Text(unit.label)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Time format",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TimeFormat.entries.forEachIndexed { index, format ->
                        SegmentedButton(
                            selected = uiState.timeFormat == format,
                            onClick = { viewModel.onTimeFormatChange(format) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeFormat.entries.size),
                        ) {
                            Text(format.label)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Predictions",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ForecastDisplay.entries.forEachIndexed { index, display ->
                        SegmentedButton(
                            selected = uiState.forecastDisplay == display,
                            onClick = { viewModel.onForecastDisplayChange(display) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ForecastDisplay.entries.size),
                        ) {
                            Text(display.label)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Glucose color scheme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    GlucoseColorScheme.entries.forEachIndexed { index, scheme ->
                        SegmentedButton(
                            selected = uiState.glucoseColorScheme == scheme,
                            onClick = { viewModel.onGlucoseColorSchemeChange(scheme) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = GlucoseColorScheme.entries.size),
                        ) {
                            Text(scheme.label)
                        }
                    }
                }
                Text(
                    text = "Dynamic: red → green → purple around target. Static: red below range, green in range, purple above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Statistics bar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeStatsFace.entries.forEach { face ->
                        FilterChip(
                            selected = uiState.homeStatsFace == face,
                            onClick = { viewModel.onHomeStatsFaceChange(face) },
                            label = { Text(face.label) },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                LabeledSwitch(
                    label = "Show current time line",
                    checked = uiState.showNowLine,
                    onCheckedChange = viewModel::onShowNowLineChange,
                )
                Text(
                    text = "Draws a vertical line on the chart at the current time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))
                LabeledSwitch(
                    label = "Keep display awake",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = viewModel::onKeepScreenOnChange,
                )
                Text(
                    text = "Stops the screen from dimming or turning off while the app is open. Uses more battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsSection(title = "Background sync") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackgroundMode.entries.forEach { mode ->
                        BackgroundModeOption(
                            label = mode.label,
                            description = mode.description,
                            selected = uiState.backgroundMode == mode,
                            onSelect = { viewModel.onBackgroundModeChange(mode) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Refresh interval",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val intervalOptions = uiState.backgroundMode.allowedRefreshIntervals()
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    intervalOptions.forEachIndexed { index, minutes ->
                        SegmentedButton(
                            selected = uiState.refreshIntervalMinutes == minutes,
                            onClick = { viewModel.onRefreshIntervalChange(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = intervalOptions.size),
                        ) {
                            Text(if (minutes >= 60) "${minutes / 60}h" else "${minutes}m")
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "Alarms") {
                LabeledSwitch(
                    label = "Enable alarms",
                    checked = uiState.alarms.alarmsEnabled,
                    onCheckedChange = { viewModel.onAlarmSettingsChange(uiState.alarms.copy(alarmsEnabled = it)) },
                )
                if (uiState.alarms.alarmsEnabled) {
                    Spacer(Modifier.height(12.dp))
                    LabeledSwitch(
                        label = "Sound",
                        checked = uiState.alarms.soundEnabled,
                        onCheckedChange = { viewModel.onAlarmSettingsChange(uiState.alarms.copy(soundEnabled = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = "Vibration",
                        checked = uiState.alarms.vibrationEnabled,
                        onCheckedChange = { viewModel.onAlarmSettingsChange(uiState.alarms.copy(vibrationEnabled = it)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = "Require acknowledgement",
                        checked = uiState.alarms.requireAcknowledgement,
                        onCheckedChange = {
                            viewModel.onAlarmSettingsChange(
                                uiState.alarms.copy(
                                    requireAcknowledgement = it,
                                    repeatIfNotAcknowledged = it && uiState.alarms.repeatIfNotAcknowledged,
                                ),
                            )
                        },
                    )
                    if (uiState.alarms.requireAcknowledgement) {
                        Spacer(Modifier.height(8.dp))
                        LabeledSwitch(
                            label = "Repeat if not acknowledged",
                            checked = uiState.alarms.repeatIfNotAcknowledged,
                            onCheckedChange = {
                                viewModel.onAlarmSettingsChange(uiState.alarms.copy(repeatIfNotAcknowledged = it))
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = "Predicted high (Reese Mode)",
                        checked = uiState.alarms.predictedHighEnabled,
                        onCheckedChange = { viewModel.onAlarmSettingsChange(uiState.alarms.copy(predictedHighEnabled = it)) },
                        onInfoClick = { showPredictedHighInfo = true },
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledSwitch(
                        label = "No data",
                        checked = uiState.alarms.noDataEnabled,
                        onCheckedChange = { viewModel.onAlarmSettingsChange(uiState.alarms.copy(noDataEnabled = it)) },
                    )
                    if (uiState.alarms.noDataEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Alert if no new glucose data for",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            NO_DATA_MINUTES_OPTIONS.forEachIndexed { index, minutes ->
                                SegmentedButton(
                                    selected = uiState.alarms.noDataMinutes == minutes,
                                    onClick = {
                                        viewModel.onAlarmSettingsChange(uiState.alarms.copy(noDataMinutes = minutes))
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = NO_DATA_MINUTES_OPTIONS.size,
                                    ),
                                ) {
                                    Text("$minutes min")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsSubsection(title = "Alarm Thresholds") {
                        ThresholdRow(
                            label = "Urgent high",
                            dotColor = TrioGlucoseUrgent,
                            enabled = uiState.alarms.urgentHigh.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(
                                    uiState.alarms.copy(urgentHigh = uiState.alarms.urgentHigh.copy(enabled = it)),
                                )
                            },
                            valueMgDl = uiState.alarms.urgentHigh.thresholdMgDl,
                            unit = uiState.glucoseUnit,
                            onValueChange = {
                                viewModel.onAlarmSettingsChange(
                                    uiState.alarms.copy(urgentHigh = uiState.alarms.urgentHigh.copy(thresholdMgDl = it)),
                                )
                            },
                        )
                        ThresholdRow(
                            label = "High",
                            dotColor = TrioGlucoseHigh,
                            enabled = uiState.alarms.high.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(uiState.alarms.copy(high = uiState.alarms.high.copy(enabled = it)))
                            },
                            valueMgDl = uiState.alarms.high.thresholdMgDl,
                            unit = uiState.glucoseUnit,
                            onValueChange = {
                                viewModel.onAlarmSettingsChange(uiState.alarms.copy(high = uiState.alarms.high.copy(thresholdMgDl = it)))
                            },
                        )
                        ThresholdRow(
                            label = "Low",
                            dotColor = TrioGlucoseLow,
                            enabled = uiState.alarms.low.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(uiState.alarms.copy(low = uiState.alarms.low.copy(enabled = it)))
                            },
                            valueMgDl = uiState.alarms.low.thresholdMgDl,
                            unit = uiState.glucoseUnit,
                            onValueChange = {
                                viewModel.onAlarmSettingsChange(uiState.alarms.copy(low = uiState.alarms.low.copy(thresholdMgDl = it)))
                            },
                        )
                        ThresholdRow(
                            label = "Urgent low",
                            dotColor = TrioGlucoseUrgent,
                            enabled = uiState.alarms.urgentLow.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(
                                    uiState.alarms.copy(urgentLow = uiState.alarms.urgentLow.copy(enabled = it)),
                                )
                            },
                            valueMgDl = uiState.alarms.urgentLow.thresholdMgDl,
                            unit = uiState.glucoseUnit,
                            onValueChange = {
                                viewModel.onAlarmSettingsChange(
                                    uiState.alarms.copy(urgentLow = uiState.alarms.urgentLow.copy(thresholdMgDl = it)),
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "Android System Permissions") {
                PermissionsSection()
            }
        }

        item {
            SettingsSection(title = "Diagnostics") {
                DiagnosticsSection(
                    onShareLog = { viewModel.logFile() },
                    onClearLog = viewModel::clearLog,
                )
            }
        }

        item {
            SettingsSection(title = "Information") {
                InformationSection()
            }
        }
    }
}
