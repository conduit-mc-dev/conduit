package dev.conduit.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.theme.*

@Composable
fun DeleteInstanceDialog(instanceName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Surface,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StateCrashed, modifier = Modifier.size(32.dp)) },
        title = { Text("Delete Server", color = TextPrimary) },
        text = {
            Column {
                Text("Server: $instanceName", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text("All server data will be permanently deleted.", style = MaterialTheme.typography.bodySmall, color = StateCrashed)
            }
        },
        confirmButton = { ActionButton("Delete Server", ButtonVariant.Danger, onClick = onConfirm) },
        dismissButton = { ActionButton("Cancel", ButtonVariant.Secondary, onClick = onDismiss) },
    )
}

@Composable
fun UnsavedChangesDialog(changes: List<Pair<String, String>>, onDiscard: () -> Unit, onCancel: () -> Unit, onSaveAndLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel, containerColor = Surface,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = StateInstalling, modifier = Modifier.size(32.dp)) },
        title = { Text("Unsaved Changes", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                changes.forEach { (key, change) -> Text("$key: $change", style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
            }
        },
        confirmButton = { ActionButton("Save & Leave", ButtonVariant.Primary, onClick = onSaveAndLeave) },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Discard", ButtonVariant.Danger, onClick = onDiscard)
                ActionButton("Cancel", ButtonVariant.Secondary, onClick = onCancel)
            }
        },
    )
}

@Composable
fun ForgetDaemonDialog(daemonName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Surface,
        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StateCrashed, modifier = Modifier.size(32.dp)) },
        title = { Text("Forget Daemon", color = TextPrimary) },
        text = {
            Column {
                Text("Daemon: $daemonName", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Text("Server data on the daemon is NOT affected.", style = MaterialTheme.typography.bodySmall, color = StateCrashed)
            }
        },
        confirmButton = { ActionButton("Forget Daemon", ButtonVariant.Danger, onClick = onConfirm) },
        dismissButton = { ActionButton("Cancel", ButtonVariant.Secondary, onClick = onDismiss) },
    )
}
