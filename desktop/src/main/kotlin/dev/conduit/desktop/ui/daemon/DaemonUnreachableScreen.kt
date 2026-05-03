package dev.conduit.desktop.ui.daemon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.WsConnectionState
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun DaemonUnreachableScreen(connectionState: WsConnectionState, daemonName: String, onRetry: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(Background), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (connectionState == WsConnectionState.RECONNECTING) {
            val alpha = rememberBreathingAlpha()
            Icon(Icons.Default.Refresh, contentDescription = null, tint = DaemonReconnecting.copy(alpha = alpha), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Connection lost", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Reconnecting to $daemonName...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        } else {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = DaemonOffline, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Daemon Unreachable", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Cannot connect to $daemonName. The daemon may be offline or the network is unreachable.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Retry", ButtonVariant.Primary, onClick = onRetry)
                ActionButton("Edit", ButtonVariant.Secondary, onClick = onEdit)
            }
        }
    }
}
