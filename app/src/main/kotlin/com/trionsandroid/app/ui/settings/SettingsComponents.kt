package com.trionsandroid.app.ui.settings

import android.Manifest
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
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
import com.trionsandroid.app.data.settings.GlucoseUnit
import com.trionsandroid.app.data.settings.format
import java.io.File

/** A top-level, card-styled settings group. Collapsed by default so the screen has room to grow
 *  without becoming an ever-longer scroll — expand state is local to the composable, so it
 *  resets to collapsed each time the Settings screen is (re)entered. */
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

/** A lighter-weight collapsible group nested *inside* a [SettingsSection] (no card of its own —
 *  stacking cards inside cards reads as noisy). Collapsed by default, same as the top-level ones. */
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
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    /** When set, shows an (i) button after the label that calls this. */
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

/** Explains the Predicted High alarm — "Reese mode". Plain language, kept deliberately short. */
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
                "When Reese sleeps in and his glucose is rising slowly, his Mom would like an alert " +
                    "so she can make sure the necessary adjustments are made.",
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

@Composable
fun ThresholdRow(
    label: String,
    dotColor: Color,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    valueMgDl: Int,
    unit: GlucoseUnit,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        if (enabled) {
            IconButton(onClick = { onValueChange((valueMgDl - 5).coerceAtLeast(40)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease $label threshold")
            }
            Text(
                text = "${unit.format(valueMgDl)} ${unit.label}",
                modifier = Modifier.width(84.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { onValueChange((valueMgDl + 5).coerceAtMost(400)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label threshold")
            }
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
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
                    context.startActivity(Intent.createChooser(intent, "Share TrioNS log"))
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
