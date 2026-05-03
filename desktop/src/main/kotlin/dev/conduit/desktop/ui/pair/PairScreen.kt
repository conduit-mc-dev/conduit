package dev.conduit.desktop.ui.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PairScreen(onPaired: (daemonId: String) -> Unit, daemonManager: DaemonManager = org.koin.compose.koinInject()) {
    var address by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8080") }
    var daemonName by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Column(modifier = Modifier.width(440.dp).clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(14.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Text("Pair Daemon", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }
            HorizontalDivider(color = Border)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server Address", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    OutlinedTextField(value = address, onValueChange = { address = it }, placeholder = { Text("192.168.1.100", color = TextMuted) },
                        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                }
                Column(modifier = Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Port", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    OutlinedTextField(value = port, onValueChange = { port = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daemon Name", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(value = daemonName, onValueChange = { daemonName = it }, placeholder = { Text("Home VPS", color = TextMuted) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pairing Code", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it }, placeholder = { Text("XXXX-XXXX-XXXX", color = TextMuted) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily, color = TextPrimary), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
            }

            Text("Pairing code shown in Daemon console", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StateCrashed) }

            ActionButton("Connect", ButtonVariant.Primary,
                onClick = {
                    isConnecting = true; error = null
                    scope.launch {
                        try {
                            val url = "http://${address.ifBlank { "localhost" }}:$port"
                            val client = ConduitApiClient(url)
                            val result = client.confirmPairing(pairingCode, daemonName.ifBlank { "Daemon" })
                            daemonManager.addDaemon(result.daemonId, daemonName.ifBlank { "Daemon" }, url, result.token)
                            daemonManager.saveSession(url, result.token, result.daemonId, daemonName)
                            onPaired(result.daemonId)
                        } catch (e: Exception) { error = "Connection failed: ${e.message}" }
                        finally { isConnecting = false }
                    }
                },
                enabled = address.isNotBlank() && pairingCode.isNotBlank() && !isConnecting,
                modifier = Modifier.fillMaxWidth())
        }
    }
}
