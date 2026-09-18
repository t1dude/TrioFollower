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
import androidx.compose.material3.Button
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
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.allowedRefreshIntervals
import com.trionsandroid.app.ui.theme.TrioGlucoseHigh
import com.trionsandroid.app.ui.theme.TrioGlucoseLow
import com.trionsandroid.app.ui.theme.TrioGlucoseUrgent
import com.trionsandroid.app.data.settings.BackgroundMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showToken by remember { mutableStateOf(false) }

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
            SettingsSection(title = "Nightscout") {
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
            }
        }

        item {
            SettingsSection(title = "Units") {
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
                    Spacer(Modifier.height(16.dp))
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
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(8.dp))
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

        item {
            SettingsSection(title = "Permissions") {
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
    }
}
