package dev.conduit.desktop.ui.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import dev.conduit.desktop.ui.daemon.DaemonViewModel
import dev.conduit.desktop.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DaemonForm(
    daemonId: String?,
    onDone: (daemonId: String) -> Unit,
    onBack: (() -> Unit)? = null,
    daemonManager: DaemonManager = org.koin.compose.koinInject(),
) {
    val isEditMode = daemonId != null
    val editViewModel: DaemonViewModel = org.koin.compose.viewmodel.koinViewModel()

    val editState = if (isEditMode) editViewModel.editState.collectAsState().value else null
    LaunchedEffect(daemonId) { if (daemonId != null) editViewModel.loadDaemon(daemonId) }

    var pairAddress by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("9147") }
    var pairName by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var pairError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val name = if (isEditMode) editState?.name ?: "" else pairName
    val address = if (isEditMode) editState?.address ?: "" else pairAddress
    val port = if (isEditMode) editState?.port ?: "8080" else pairPort
    val error = if (isEditMode) editState?.error else pairError
    val isBusy = if (isEditMode) editState?.isSaving == true else isConnecting

    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEditMode && onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Icon(
                    if (isEditMode) Icons.Default.Edit else Icons.Default.Link,
                    contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp),
                )
                Text(
                    if (isEditMode) "Edit Daemon" else "Pair Daemon",
                    style = MaterialTheme.typography.headlineMedium, color = TextPrimary,
                )
            }
            HorizontalDivider(color = Border)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldColumn("Server Address", address,
                    onChange = { if (isEditMode) editViewModel.updateAddress(it) else pairAddress = it },
                    placeholder = if (isEditMode) "192.168.1.100" else "localhost",
                    modifier = Modifier.weight(1f),
                )
                FieldColumn("Port", port,
                    onChange = { if (isEditMode) editViewModel.updatePort(it) else pairPort = it },
                    placeholder = if (isEditMode) "8080" else "9147",
                    modifier = Modifier.width(80.dp),
                )
            }

            FieldColumn("Daemon Name", name,
                onChange = { if (isEditMode) editViewModel.updateName(it) else pairName = it },
                placeholder = "Home VPS",
            )

            if (!isEditMode) {
                FieldColumn("Pairing Code", pairCode,
                    onChange = { pairCode = it },
                    placeholder = "000000",
                    monospace = true,
                )
                Text("Pairing code shown in Daemon console", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            if (isEditMode) {
                Text("If the address changes, the app will try the existing token first. 401 triggers re-pairing.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }

            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StateCrashed) }

            if (isEditMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    ActionButton("Cancel", ButtonVariant.Default, onClick = { onBack?.invoke() })
                    ActionButton("Save", ButtonVariant.Success,
                        onClick = { editViewModel.save(daemonId) { onDone(daemonId) } },
                        enabled = !isBusy,
                    )
                }
            } else {
                ActionButton("Connect", ButtonVariant.Success,
                    onClick = {
                        isConnecting = true; pairError = null
                        scope.launch {
                            try {
                                val url = "http://${address.ifBlank { "localhost" }}:$port"
                                val client = ConduitApiClient(url)
                                val result = client.confirmPairing(pairCode, name.ifBlank { "Daemon" })
                                daemonManager.addDaemon(result.daemonId, name.ifBlank { "Daemon" }, url, result.token)
                                daemonManager.saveSession(url, result.token, result.daemonId, name)
                                onDone(result.daemonId)
                            } catch (e: Exception) { pairError = "Connection failed: ${e.message}" }
                            finally { isConnecting = false }
                        }
                    },
                    enabled = address.isNotBlank() && pairCode.isNotBlank() && !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FieldColumn(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = TextMuted) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontFamily = if (monospace) MonoFontFamily else null,
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        )
    }
}
