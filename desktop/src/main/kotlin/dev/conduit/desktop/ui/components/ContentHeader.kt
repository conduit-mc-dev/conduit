package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.core.model.InstanceState
import dev.conduit.desktop.ui.theme.*

@Composable
fun ContentHeader(
    instanceName: String,
    instanceState: InstanceState,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onKill: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().background(Surface)
            .drawBehind {
                drawLine(Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(instanceName, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight(800)), color = TextPrimary)
            StatusDot(instanceState, StatusDotSize.Medium)
            Text(
                when (instanceState) {
                    InstanceState.RUNNING -> "Running"
                    InstanceState.STOPPED -> "Stopped"
                    InstanceState.STARTING -> "Starting..."
                    InstanceState.STOPPING -> "Stopping..."
                    InstanceState.CRASHED -> "Crashed"
                    InstanceState.INITIALIZING -> "Installing"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = when (instanceState) {
                    InstanceState.RUNNING -> StateRunning
                    InstanceState.STOPPED -> StateStopped
                    InstanceState.STARTING -> StateStarting
                    InstanceState.STOPPING -> StateStopping
                    InstanceState.CRASHED -> StateCrashed
                    InstanceState.INITIALIZING -> StateInstalling
                },
            )
        }
        InstanceActionButtons(instanceState, isActionInProgress, onStart, onStop, onRestart, onKill, onDelete, onCancel)
    }
}
