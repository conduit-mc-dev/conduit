package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.core.model.InstanceState
import dev.conduit.desktop.ui.theme.*

// ── Solid-background button for dialogs (distinct from ActionButton Style E) ──
@Composable
private fun DialogButton(
    text: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = textColor),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Reusable dialog shell (S17/S18/S22 shared chrome) ──
@Composable
fun ConduitDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    iconTint: Color,
    iconBoxBg: Color,
    iconBoxBorder: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Surface,
            modifier = Modifier.widthIn(max = 380.dp).border(1.dp, Border, RoundedCornerShape(14.dp)),
        ) {
            Column(Modifier.padding(28.dp)) {
                // Header: icon box + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBoxBg)
                            .border(1.dp, iconBoxBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                // Body
                Column(content = content, modifier = Modifier.padding(bottom = 20.dp))

                // Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.align(Alignment.End),
                    content = actions,
                )
            }
        }
    }
}

// ── Reference card (used in S17 delete and S22 forget) ──
@Composable
fun DialogRefCard(name: String, info: String, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Background)
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor),
        )
        Column {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(info, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

// ── Warning box (used in S17 and S22) ──
@Composable
fun DialogWarningBox(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = StateCrashed,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StateCrashed.copy(alpha = 0.06f))
            .border(1.dp, StateCrashed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

// ── Changes list (used in S18 unsaved) ──
@Composable
fun DialogChangesList(changes: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Background)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        changes.forEach { (key, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(key, fontSize = 12.sp, color = AccentBlue, fontFamily = FontFamily.Monospace)
                Text("→", fontSize = 12.sp, color = TextMuted)
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        }
    }
}

// ── S17: Delete Instance Confirmation ──
@Composable
fun DeleteInstanceDialog(
    instanceName: String,
    instanceInfo: String,
    instanceState: InstanceState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val stateColor = when (instanceState) {
        InstanceState.RUNNING -> StateRunning
        InstanceState.STARTING -> StateStarting
        InstanceState.STOPPING -> StateStopping
        InstanceState.CRASHED -> StateCrashed
        InstanceState.INITIALIZING -> StateStarting
        InstanceState.STOPPED -> StateStopped
    }
    val stateLabel = when (instanceState) {
        InstanceState.RUNNING -> "Running"
        InstanceState.STARTING -> "Starting"
        InstanceState.STOPPING -> "Stopping"
        InstanceState.CRASHED -> "Crashed"
        InstanceState.INITIALIZING -> "Initializing"
        InstanceState.STOPPED -> "Stopped"
    }

    ConduitDialog(
        onDismiss = onDismiss,
        icon = Icons.Default.Delete,
        iconTint = StateCrashed,
        iconBoxBg = StateCrashed.copy(alpha = 0.1f),
        iconBoxBorder = StateCrashed.copy(alpha = 0.2f),
        title = "Delete Server",
        content = {
            Text(
                "Are you sure you want to delete this server? This action cannot be undone.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            DialogRefCard(
                name = instanceName,
                info = "$instanceInfo · $stateLabel",
                dotColor = stateColor,
            )
            Spacer(Modifier.height(10.dp))
            DialogWarningBox("All server data, including world files, mods, and configuration will be permanently deleted.")
        },
        actions = {
            DialogButton("Cancel", bgColor = Elevated, textColor = TextPrimary, onClick = onDismiss)
            DialogButton("Delete Server", bgColor = StateCrashed, textColor = Color.White, onClick = onConfirm)
        },
    )
}

// ── S18: Unsaved Changes Warning ──
@Composable
fun UnsavedChangesDialog(
    changes: List<Pair<String, String>>,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    onSaveAndLeave: () -> Unit,
) {
    ConduitDialog(
        onDismiss = onCancel,
        icon = Icons.Default.Warning,
        iconTint = StateInstalling,
        iconBoxBg = StateInstalling.copy(alpha = 0.1f),
        iconBoxBorder = StateInstalling.copy(alpha = 0.2f),
        title = "Unsaved Changes",
        content = {
            Text(
                "You have unsaved changes that will be lost if you navigate away.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (changes.isNotEmpty()) {
                DialogChangesList(changes)
            }
        },
        actions = {
            DialogButton("Discard", bgColor = StateCrashed, textColor = Color.White, onClick = onDiscard)
            DialogButton("Cancel", bgColor = Elevated, textColor = TextPrimary, onClick = onCancel)
            DialogButton("Save & Leave", bgColor = AccentBlue, textColor = Background, onClick = onSaveAndLeave)
        },
    )
}

// ── S22: Forget Daemon ──
@Composable
fun ForgetDaemonDialog(
    daemonName: String,
    daemonAddress: String,
    serverCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConduitDialog(
        onDismiss = onDismiss,
        icon = Icons.Default.Delete,
        iconTint = StateCrashed,
        iconBoxBg = StateCrashed.copy(alpha = 0.1f),
        iconBoxBorder = StateCrashed.copy(alpha = 0.2f),
        title = "Forget Daemon",
        content = {
            Text(
                "Remove this daemon and all its server entries from Conduit?",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            val serverText = if (serverCount == 1) "1 server" else "$serverCount servers"
            DialogRefCard(
                name = daemonName,
                info = "$daemonAddress · $serverText",
                dotColor = DaemonOnline,
            )
            Spacer(Modifier.height(10.dp))
            DialogWarningBox("Server data on the daemon is NOT affected. Only the local connection record and cached data will be removed.")
        },
        actions = {
            DialogButton("Cancel", bgColor = Elevated, textColor = TextPrimary, onClick = onDismiss)
            DialogButton("Forget Daemon", bgColor = StateCrashed, textColor = Color.White, onClick = onConfirm)
        },
    )
}
