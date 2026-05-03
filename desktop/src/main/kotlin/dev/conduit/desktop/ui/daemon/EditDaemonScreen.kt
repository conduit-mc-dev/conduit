package dev.conduit.desktop.ui.daemon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun EditDaemonScreen(daemonId: String, onBack: () -> Unit, viewModel: DaemonViewModel = org.koin.compose.viewmodel.koinViewModel()) {
    LaunchedEffect(daemonId) { viewModel.loadDaemon(daemonId) }
    val state by viewModel.editState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.width(440.dp).clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(14.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                Icon(Icons.Default.Edit, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Text("Edit Daemon", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }
            HorizontalDivider(color = Border)
            F("Daemon Name", state.name, viewModel::updateName, "Home VPS")
            F("Address", state.address, viewModel::updateAddress, "192.168.1.100")
            F("Port", state.port, viewModel::updatePort, "8080")
            Text("If the address changes, the app will try the existing token first. 401 triggers re-pairing.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StateCrashed) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                ActionButton("Cancel", ButtonVariant.Secondary, onClick = onBack)
                ActionButton("Save", ButtonVariant.Primary, onClick = { viewModel.save(daemonId, onBack) }, enabled = !state.isSaving)
            }
        }
    }
}

@Composable
private fun F(label: String, value: String, onChange: (String) -> Unit, ph: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(ph, color = TextMuted) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
    }
}
