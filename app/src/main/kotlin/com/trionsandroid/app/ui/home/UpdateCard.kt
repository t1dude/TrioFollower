package com.trionsandroid.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trionsandroid.app.data.update.UpdateInfo

private const val MAX_NOTE_LINES = 8

/** "Update available" card with the release notes. Update opens the release page in the browser. */
@Composable
fun UpdateCard(update: UpdateInfo, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notes = update.notes.lines()
        .map { it.trim().trimStart('#', '*', '-', ' ').trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_NOTE_LINES)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Version ${update.version} is available", style = MaterialTheme.typography.titleMedium)
            if (notes.isNotEmpty()) {
                Text("What's new", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                notes.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(update.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("Update") }
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    }
}
