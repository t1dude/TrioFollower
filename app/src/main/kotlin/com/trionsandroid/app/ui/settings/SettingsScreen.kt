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
import com.trionsandroid.app.data.settings.BolusDisplayThreshold
import com.trionsandroid.app.data.settings.ForecastDisplay
import com.trionsandroid.app.data.settings.GlucoseColorScheme
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.HomeStatsFace
import com.trionsandroid.app.data.settings.NO_DATA_MINUTES_OPTIONS
import com.trionsandroid.app.data.settings.NOT_LOOPING_MINUTES_OPTIONS
import com.trionsandroid.app.data.settings.TimeFormat
import com.trionsandroid.app.data.settings.allowedRefreshIntervals
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.timeFormatter
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioGlucoseUrgent
import com.trionsandroid.app.data.settings.BackgroundMode
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(expandBasicSettings: Boolean = false, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }
    var showPredictedHighInfo by remember { mutableStateOf(false) }
    if (showPredictedHighInfo) PredictedHighInfoSheet(onDismiss = { showPredictedHighInfo = false })
    var showRandomAlarmInfo by remember { mutableStateOf(false) }
    if (showRandomAlarmInfo) RandomAlarmInfoSheet(onDismiss = { showRandomAlarmInfo = false })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 72.dp),
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
            SettingsSection(title = "User Interface") {
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
                Text(
                    text = "Bolus Display Threshold",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BolusDisplayThreshold.entries.forEach { threshold ->
                        FilterChip(
                            selected = uiState.bolusDisplayThreshold == threshold,
                            onClick = { viewModel.onBolusDisplayThresholdChange(threshold) },
                            label = { Text(threshold.label) },
                        )
                    }
                }
                Text(
                    text = "Boluses below the threshold keep their marker on the chart but not the amount label.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
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
            val alarms = uiState.alarms
            val glucoseUnit = uiState.glucoseUnit

            SettingsSection(title = "Alarms") {
                LabeledSwitch(
                    label = "Enable alarms",
                    checked = alarms.alarmsEnabled,
                    onCheckedChange = { viewModel.onAlarmSettingsChange(alarms.copy(alarmsEnabled = it)) },
                )
                if (alarms.alarmsEnabled) {
                    Spacer(Modifier.height(16.dp))
                    SettingsSubsection(title = "Day and Night Windows") {
                        DayNightWindowsSection(
                            window = alarms.dayNightWindow,
                            onWindowChange = { viewModel.onAlarmSettingsChange(alarms.copy(dayNightWindow = it)) },
                            is24Hour = uiState.timeFormat == TimeFormat.HOUR_24,
                            formatter = uiState.timeFormat.timeFormatter(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsSubsection(title = "Glucose Alarms") {
                        AlarmAccordion(
                            title = "Urgent high",
                            enabled = alarms.urgentHigh.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(urgentHigh = alarms.urgentHigh.copy(enabled = it)))
                            },
                            behavior = alarms.urgentHigh.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(urgentHigh = alarms.urgentHigh.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    dotColor = TrioGlucoseUrgent,
                                    valueText = "${glucoseUnit.format(alarms.urgentHigh.thresholdMgDl)} ${glucoseUnit.label}",
                                    onDecrease = {
                                        val value = (alarms.urgentHigh.thresholdMgDl - 5).coerceAtLeast(40)
                                        viewModel.onAlarmSettingsChange(alarms.copy(urgentHigh = alarms.urgentHigh.copy(thresholdMgDl = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.urgentHigh.thresholdMgDl + 5).coerceAtMost(400)
                                        viewModel.onAlarmSettingsChange(alarms.copy(urgentHigh = alarms.urgentHigh.copy(thresholdMgDl = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "High",
                            enabled = alarms.high.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(high = alarms.high.copy(enabled = it)))
                            },
                            behavior = alarms.high.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(high = alarms.high.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    dotColor = TrioGlucoseHigh,
                                    valueText = "${glucoseUnit.format(alarms.high.thresholdMgDl)} ${glucoseUnit.label}",
                                    onDecrease = {
                                        val value = (alarms.high.thresholdMgDl - 5).coerceAtLeast(40)
                                        viewModel.onAlarmSettingsChange(alarms.copy(high = alarms.high.copy(thresholdMgDl = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.high.thresholdMgDl + 5).coerceAtMost(400)
                                        viewModel.onAlarmSettingsChange(alarms.copy(high = alarms.high.copy(thresholdMgDl = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "Low",
                            enabled = alarms.low.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(low = alarms.low.copy(enabled = it)))
                            },
                            behavior = alarms.low.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(low = alarms.low.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    dotColor = TrioGlucoseLow,
                                    valueText = "${glucoseUnit.format(alarms.low.thresholdMgDl)} ${glucoseUnit.label}",
                                    onDecrease = {
                                        val value = (alarms.low.thresholdMgDl - 5).coerceAtLeast(40)
                                        viewModel.onAlarmSettingsChange(alarms.copy(low = alarms.low.copy(thresholdMgDl = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.low.thresholdMgDl + 5).coerceAtMost(400)
                                        viewModel.onAlarmSettingsChange(alarms.copy(low = alarms.low.copy(thresholdMgDl = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "Urgent low",
                            enabled = alarms.urgentLow.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(urgentLow = alarms.urgentLow.copy(enabled = it)))
                            },
                            behavior = alarms.urgentLow.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(urgentLow = alarms.urgentLow.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    dotColor = TrioGlucoseUrgent,
                                    valueText = "${glucoseUnit.format(alarms.urgentLow.thresholdMgDl)} ${glucoseUnit.label}",
                                    onDecrease = {
                                        val value = (alarms.urgentLow.thresholdMgDl - 5).coerceAtLeast(40)
                                        viewModel.onAlarmSettingsChange(alarms.copy(urgentLow = alarms.urgentLow.copy(thresholdMgDl = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.urgentLow.thresholdMgDl + 5).coerceAtMost(400)
                                        viewModel.onAlarmSettingsChange(alarms.copy(urgentLow = alarms.urgentLow.copy(thresholdMgDl = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "Predicted high (Reese Mode)",
                            enabled = alarms.predictedHigh.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(predictedHigh = alarms.predictedHigh.copy(enabled = it)))
                            },
                            behavior = alarms.predictedHigh.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(predictedHigh = alarms.predictedHigh.copy(behavior = it)))
                            },
                            onInfoClick = { showPredictedHighInfo = true },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsSubsection(title = "Additional Alarms") {
                        AlarmAccordion(
                            title = "No data",
                            enabled = alarms.noData.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(noData = alarms.noData.copy(enabled = it)))
                            },
                            behavior = alarms.noData.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(noData = alarms.noData.copy(behavior = it)))
                            },
                            valueContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Alert if no new glucose data for",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        NO_DATA_MINUTES_OPTIONS.forEachIndexed { index, minutes ->
                                            SegmentedButton(
                                                selected = alarms.noData.minutes == minutes,
                                                onClick = {
                                                    viewModel.onAlarmSettingsChange(alarms.copy(noData = alarms.noData.copy(minutes = minutes)))
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index = index, count = NO_DATA_MINUTES_OPTIONS.size),
                                            ) {
                                                Text("$minutes min")
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        AlarmAccordion(
                            title = "Not looping",
                            enabled = alarms.notLooping.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(notLooping = alarms.notLooping.copy(enabled = it)))
                            },
                            behavior = alarms.notLooping.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(notLooping = alarms.notLooping.copy(behavior = it)))
                            },
                            valueContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Alert if no confirmed loop for",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        NOT_LOOPING_MINUTES_OPTIONS.forEachIndexed { index, minutes ->
                                            SegmentedButton(
                                                selected = alarms.notLooping.minutes == minutes,
                                                onClick = {
                                                    viewModel.onAlarmSettingsChange(alarms.copy(notLooping = alarms.notLooping.copy(minutes = minutes)))
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index = index, count = NOT_LOOPING_MINUTES_OPTIONS.size),
                                            ) {
                                                Text("$minutes min")
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        AlarmAccordion(
                            title = "Reservoir low",
                            enabled = alarms.reservoir.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(reservoir = alarms.reservoir.copy(enabled = it)))
                            },
                            behavior = alarms.reservoir.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(reservoir = alarms.reservoir.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    valueText = String.format(Locale.getDefault(), "%.1f U", alarms.reservoir.thresholdUnits),
                                    onDecrease = {
                                        val value = (alarms.reservoir.thresholdUnits - 5).coerceAtLeast(5.0)
                                        viewModel.onAlarmSettingsChange(alarms.copy(reservoir = alarms.reservoir.copy(thresholdUnits = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.reservoir.thresholdUnits + 5).coerceAtMost(100.0)
                                        viewModel.onAlarmSettingsChange(alarms.copy(reservoir = alarms.reservoir.copy(thresholdUnits = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "Pump change due",
                            enabled = alarms.pumpChange.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(pumpChange = alarms.pumpChange.copy(enabled = it)))
                            },
                            behavior = alarms.pumpChange.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(pumpChange = alarms.pumpChange.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Time left",
                                    valueText = "${alarms.pumpChange.hoursThreshold} h",
                                    onDecrease = {
                                        val value = (alarms.pumpChange.hoursThreshold - 1).coerceAtLeast(1)
                                        viewModel.onAlarmSettingsChange(alarms.copy(pumpChange = alarms.pumpChange.copy(hoursThreshold = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.pumpChange.hoursThreshold + 1).coerceAtMost(48)
                                        viewModel.onAlarmSettingsChange(alarms.copy(pumpChange = alarms.pumpChange.copy(hoursThreshold = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "CGM due",
                            enabled = alarms.sensorChange.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(sensorChange = alarms.sensorChange.copy(enabled = it)))
                            },
                            behavior = alarms.sensorChange.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(sensorChange = alarms.sensorChange.copy(behavior = it)))
                            },
                            valueContent = {
                                StepperRow(
                                    label = "Time left",
                                    valueText = "${alarms.sensorChange.hoursThreshold} h",
                                    onDecrease = {
                                        val value = (alarms.sensorChange.hoursThreshold - 1).coerceAtLeast(1)
                                        viewModel.onAlarmSettingsChange(alarms.copy(sensorChange = alarms.sensorChange.copy(hoursThreshold = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.sensorChange.hoursThreshold + 1).coerceAtMost(48)
                                        viewModel.onAlarmSettingsChange(alarms.copy(sensorChange = alarms.sensorChange.copy(hoursThreshold = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "IOB",
                            enabled = alarms.iob.enabled,
                            onEnabledChange = { viewModel.onAlarmSettingsChange(alarms.copy(iob = alarms.iob.copy(enabled = it))) },
                            behavior = alarms.iob.behavior,
                            onBehaviorChange = { viewModel.onAlarmSettingsChange(alarms.copy(iob = alarms.iob.copy(behavior = it))) },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    valueText = String.format(Locale.getDefault(), "%.1f U", alarms.iob.thresholdUnits),
                                    onDecrease = {
                                        val value = (alarms.iob.thresholdUnits - 0.5).coerceAtLeast(0.5)
                                        viewModel.onAlarmSettingsChange(alarms.copy(iob = alarms.iob.copy(thresholdUnits = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.iob.thresholdUnits + 0.5).coerceAtMost(30.0)
                                        viewModel.onAlarmSettingsChange(alarms.copy(iob = alarms.iob.copy(thresholdUnits = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "COB",
                            enabled = alarms.cob.enabled,
                            onEnabledChange = { viewModel.onAlarmSettingsChange(alarms.copy(cob = alarms.cob.copy(enabled = it))) },
                            behavior = alarms.cob.behavior,
                            onBehaviorChange = { viewModel.onAlarmSettingsChange(alarms.copy(cob = alarms.cob.copy(behavior = it))) },
                            valueContent = {
                                StepperRow(
                                    label = "Threshold",
                                    valueText = "${alarms.cob.thresholdGrams.toInt()} g",
                                    onDecrease = {
                                        val value = (alarms.cob.thresholdGrams - 5).coerceAtLeast(5.0)
                                        viewModel.onAlarmSettingsChange(alarms.copy(cob = alarms.cob.copy(thresholdGrams = value)))
                                    },
                                    onIncrease = {
                                        val value = (alarms.cob.thresholdGrams + 5).coerceAtMost(200.0)
                                        viewModel.onAlarmSettingsChange(alarms.copy(cob = alarms.cob.copy(thresholdGrams = value)))
                                    },
                                )
                            },
                        )
                        AlarmAccordion(
                            title = "Trio phone battery low",
                            enabled = alarms.lowPhoneBattery.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(lowPhoneBattery = alarms.lowPhoneBattery.copy(enabled = it)))
                            },
                            behavior = alarms.lowPhoneBattery.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(lowPhoneBattery = alarms.lowPhoneBattery.copy(behavior = it)))
                            },
                            valueContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StepperRow(
                                        label = "Threshold",
                                        valueText = "${alarms.lowPhoneBattery.percent}%",
                                        onDecrease = {
                                            val value = (alarms.lowPhoneBattery.percent - 5).coerceAtLeast(5)
                                            viewModel.onAlarmSettingsChange(
                                                alarms.copy(lowPhoneBattery = alarms.lowPhoneBattery.copy(percent = value)),
                                            )
                                        },
                                        onIncrease = {
                                            val value = (alarms.lowPhoneBattery.percent + 5).coerceAtMost(50)
                                            viewModel.onAlarmSettingsChange(
                                                alarms.copy(lowPhoneBattery = alarms.lowPhoneBattery.copy(percent = value)),
                                            )
                                        },
                                    )
                                    Text(
                                        text = "The battery of the phone running Trio, as it reports to Nightscout — not this phone.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                        AlarmAccordion(
                            title = "Random alarm",
                            enabled = alarms.randomAlarm.enabled,
                            onEnabledChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(randomAlarm = alarms.randomAlarm.copy(enabled = it)))
                            },
                            behavior = alarms.randomAlarm.behavior,
                            onBehaviorChange = {
                                viewModel.onAlarmSettingsChange(alarms.copy(randomAlarm = alarms.randomAlarm.copy(behavior = it)))
                            },
                            onInfoClick = { showRandomAlarmInfo = true },
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
            SettingsSection(title = "Information and Releases") {
                InformationSection(
                    checkForUpdates = uiState.checkForUpdates,
                    onCheckForUpdatesChange = viewModel::onCheckForUpdatesChange,
                    updateStatus = uiState.updateStatus,
                    onCheckNow = viewModel::checkForUpdatesNow,
                )
            }
        }
    }
}
