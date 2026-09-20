package com.trionsandroid.app.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.trionsandroid.app.ui.theme.TrioLoopRed
import com.trionsandroid.app.ui.theme.TrioWarningOrange

/** True when the app may post notifications: the runtime permission is granted and notifications
 *  haven't been switched off for the app in system settings. */
fun Context.notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

fun Context.batteryUnrestricted(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

/** Opens the system prompt asking to exempt the app from battery optimization. */
fun Context.requestBatteryExemption() {
    if (batteryUnrestricted()) return
    startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Warns on Home when a permission the app needs has been withheld or later revoked: without
 * notifications alarms can't alert anyone, and without a battery exemption background sync (and
 * so alarms) can be throttled. Re-checked every time the app comes to the foreground; tapping a
 * warning goes straight to fixing it, and it disappears once the permission is back.
 */
@Composable
fun PermissionWarnings(alarmsEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsOk by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOk = context.notificationsAllowed()
        batteryOk = context.batteryUnrestricted()
        onPauseOrDispose { }
    }

    if (notificationsOk && (batteryOk || !alarmsEnabled)) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notificationsOk) {
            WarningCard(
                title = "Notifications are off",
                message = "Alarms cannot alert you. Tap to turn them on.",
                color = TrioLoopRed,
                onClick = { context.openNotificationSettings() },
            )
        }
        if (!batteryOk && alarmsEnabled) {
            WarningCard(
                title = "Battery optimization is on",
                message = "Background sync and alarms may be delayed. Tap to allow.",
                color = TrioWarningOrange,
                onClick = { context.requestBatteryExemption() },
            )
        }
    }
}

@Composable
private fun WarningCard(title: String, message: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, color = color, style = MaterialTheme.typography.titleSmall)
            Text(message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
        }
    }
}
