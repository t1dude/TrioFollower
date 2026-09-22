package com.trionsandroid.app.ui.settings

import android.Manifest
import android.content.Intent
import com.trionsandroid.app.BuildConfig
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.trionsandroid.app.data.settings.AlarmBehavior
import com.trionsandroid.app.data.settings.DayNightWindow
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import com.trionsandroid.app.data.settings.toLocalTime
import com.trionsandroid.app.data.settings.toMinuteOfDay
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A collapsible, card-styled settings group (collapsed by default). */
@Composable
fun SettingsSection(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
        }
    }
}

/** A collapsible group inside a [SettingsSection], without a card of its own. */
@Composable
fun SettingsSubsection(title: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    /** Shows an (i) button after the label when set. */
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false))
            if (onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "About $label",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Explains the Predicted High alarm ("Reese mode"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictedHighInfoSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Reese mode 😉", style = MaterialTheme.typography.titleLarge)
            Text(
                "When Reese's glucose is rising slowly, his Mom would like an alert so she can make sure " +
                    "the necessary adjustments are made. It helps handle scenarios like pump failure, " +
                    "leakage, occlusions - or just sleeping in.",
            )
            Text("How it works", style = MaterialTheme.typography.titleMedium)
            Text(
                "Over the last hour, glucose has stayed in range and crept up steadily — no sudden " +
                    "jump — and at this pace it would reach the high level within the next hour.",
            )
            Text(
                "No alert if food or a bolus was logged in that hour (a fast rise after a meal is " +
                    "the regular High alert's job). Automatic SMBs don't count.",
            )
            Text(
                "After an alert, it waits an hour before alerting again.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Explains the Random Alarm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomAlarmInfoSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Random alarm 🎲", style = MaterialTheme.typography.titleLarge)
            Text(
                "If all the alarms above aren't quite enough alarm fatigue for you, this one's for you.",
            )
            Text(
                "It fires at a few random times a day — up to 4 — for no reason at all. No threshold " +
                    "to set: it's either on or off.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A value stepper row: an optional colored dot (for a glucose threshold, to match the chart),
 * a label, and a [-] value [+] group. No switch — the alarm's own enable switch lives in
 * [AlarmAccordion]'s header, once per alarm, instead of once per row inside it.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    dotColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Spacer(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $label")
        }
        Text(
            text = valueText,
            modifier = Modifier.width(84.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $label")
        }
    }
}

/** Two compact labeled switches side by side (Day/Night, Sound/Vibration). */
@Composable
fun TogglePairRow(
    leftLabel: String,
    leftChecked: Boolean,
    onLeftChange: (Boolean) -> Unit,
    rightLabel: String,
    rightChecked: Boolean,
    onRightChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TogglePairItem(leftLabel, leftChecked, onLeftChange, Modifier.weight(1f))
        TogglePairItem(rightLabel, rightChecked, onRightChange, Modifier.weight(1f))
    }
}

@Composable
private fun TogglePairItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The behavior every alarm shares: when it's allowed to fire (Day/Night — see the Day and Night
 * Windows section), how it notifies (sound/vibration), and acknowledgement. Shown identically
 * inside every [AlarmAccordion] so all ~14 alarms behave and look the same way.
 */
@Composable
fun AlarmBehaviorControls(behavior: AlarmBehavior, onBehaviorChange: (AlarmBehavior) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TogglePairRow(
            leftLabel = "Day",
            leftChecked = behavior.fireDay,
            onLeftChange = { onBehaviorChange(behavior.copy(fireDay = it)) },
            rightLabel = "Night",
            rightChecked = behavior.fireNight,
            onRightChange = { onBehaviorChange(behavior.copy(fireNight = it)) },
        )
        TogglePairRow(
            leftLabel = "Sound",
            leftChecked = behavior.soundEnabled,
            onLeftChange = { onBehaviorChange(behavior.copy(soundEnabled = it)) },
            rightLabel = "Vibration",
            rightChecked = behavior.vibrationEnabled,
            onRightChange = { onBehaviorChange(behavior.copy(vibrationEnabled = it)) },
        )
        LabeledSwitch(
            label = "Require acknowledgement",
            checked = behavior.requireAcknowledgement,
            onCheckedChange = {
                onBehaviorChange(
                    behavior.copy(requireAcknowledgement = it, repeatIfNotAcknowledged = it && behavior.repeatIfNotAcknowledged),
                )
            },
        )
        if (behavior.requireAcknowledgement) {
            LabeledSwitch(
                label = "Repeat if not acknowledged",
                checked = behavior.repeatIfNotAcknowledged,
                onCheckedChange = { onBehaviorChange(behavior.copy(repeatIfNotAcknowledged = it)) },
            )
        }
    }
}

/**
 * One alarm as a collapsible card: a header (title, optional (i) info, the enable switch, and an
 * expand/collapse chevron — all visible even collapsed, so the user can see at a glance which
 * alarms are on without opening every one) and, when expanded, that alarm's own value control
 * ([valueContent], e.g. a [StepperRow] or a threshold/minutes picker) followed by its
 * [AlarmBehaviorControls]. Collapsed by default so a screen of ~14 of these doesn't overwhelm.
 */
@Composable
fun AlarmAccordion(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    behavior: AlarmBehavior,
    onBehaviorChange: (AlarmBehavior) -> Unit,
    onInfoClick: (() -> Unit)? = null,
    valueContent: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "About $title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    valueContent?.invoke()
                    AlarmBehaviorControls(behavior, onBehaviorChange)
                }
            }
        }
    }
}

/**
 * The user's Day and Night time-of-day windows (each may wrap past midnight). Each alarm decides
 * for itself whether it's allowed to fire during Day, Night, both (the default) or neither.
 */
@Composable
fun DayNightWindowsSection(window: DayNightWindow, onWindowChange: (DayNightWindow) -> Unit, is24Hour: Boolean, formatter: DateTimeFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Each alarm below can be set to fire during Day, Night, both or neither.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Day", color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimePickerField(
                label = "From",
                time = window.dayStartMinute.toLocalTime(),
                is24Hour = is24Hour,
                formatter = formatter,
                modifier = Modifier.weight(1f),
                onTimeChange = { onWindowChange(window.copy(dayStartMinute = it.toMinuteOfDay())) },
            )
            TimePickerField(
                label = "To",
                time = window.dayEndMinute.toLocalTime(),
                is24Hour = is24Hour,
                formatter = formatter,
                modifier = Modifier.weight(1f),
                onTimeChange = { onWindowChange(window.copy(dayEndMinute = it.toMinuteOfDay())) },
            )
        }
        Text("Night", color = MaterialTheme.colorScheme.onSurface)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimePickerField(
                label = "From",
                time = window.nightStartMinute.toLocalTime(),
                is24Hour = is24Hour,
                formatter = formatter,
                modifier = Modifier.weight(1f),
                onTimeChange = { onWindowChange(window.copy(nightStartMinute = it.toMinuteOfDay())) },
            )
            TimePickerField(
                label = "To",
                time = window.nightEndMinute.toLocalTime(),
                is24Hour = is24Hour,
                formatter = formatter,
                modifier = Modifier.weight(1f),
                onTimeChange = { onWindowChange(window.copy(nightEndMinute = it.toMinuteOfDay())) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    label: String,
    time: LocalTime,
    is24Hour: Boolean,
    formatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
    onTimeChange: (LocalTime) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time.format(formatter), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (showPicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun ConnectionStatusRow(state: ConnectionTestState) {
    when (state) {
        ConnectionTestState.Idle -> Unit
        ConnectionTestState.Loading -> Text(
            text = "Testing…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        ConnectionTestState.Success -> Text(
            text = "Connected",
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 8.dp),
        )
        is ConnectionTestState.Error -> Text(
            text = state.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun PermissionsSection() {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(false) }
    var batteryUnrestricted by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    LifecycleResumeEffect(Unit) {
        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        batteryUnrestricted = ContextCompat.getSystemService(context, PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
        onPauseOrDispose { }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionRow(
            title = "Notifications",
            subtitle = "Needed to show glucose alarms",
            granted = notificationsGranted,
            onRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionRow(
            title = "Ignore battery optimization",
            subtitle = "Keeps background sync and alarms reliable",
            granted = batteryUnrestricted,
            onRequest = {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            TextButton(onClick = onRequest) { Text("Grant") }
        }
    }
}

@Composable
fun BackgroundModeOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun DiagnosticsSection(onShareLog: () -> File, onClearLog: () -> Unit) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Share a log of recent app activity and Nightscout requests — useful when " +
                "troubleshooting a connection or sync issue. The log includes the actual glucose " +
                "and treatment data returned by Nightscout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val file = onShareLog()
                    if (!file.exists() || file.length() == 0L) return@Button
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share TrioFollower log"))
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Share log file")
            }
            OutlinedButton(onClick = onClearLog, modifier = Modifier.weight(1f)) {
                Text("Clear log")
            }
        }
    }
}

private const val REPO_URL = "https://github.com/t1dude/TrioFollower"
private const val README_URL = "$REPO_URL#readme"

/** Links that open in the device's default browser. */
@Composable
fun InformationSection(
    checkForUpdates: Boolean,
    onCheckForUpdatesChange: (Boolean) -> Unit,
    updateStatus: String?,
    onCheckNow: () -> Unit,
) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LabeledSwitch(
            label = "Check for updates",
            checked = checkForUpdates,
            onCheckedChange = onCheckForUpdatesChange,
        )
        Text(
            text = "Looks for a newer version on GitHub about once a day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCheckNow) { Text("Check now") }
        updateStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        InfoLinkRow(title = "Read the README", subtitle = "What the app does, requirements and disclaimer") { open(README_URL) }
        InfoLinkRow(title = "GitHub repository", subtitle = "Source code, updates and issues") { open(REPO_URL) }
    }
}

@Composable
private fun InfoLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Opens in browser",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
