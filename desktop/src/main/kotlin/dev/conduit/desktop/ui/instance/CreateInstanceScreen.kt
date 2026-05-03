package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun CreateInstanceScreen(
    daemonId: String,
    onCreated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateInstanceViewModel = org.koin.compose.viewmodel.koinViewModel { org.koin.core.parameter.parametersOf(daemonId) },
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onCancel, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Text("Create New Server", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            }
            HorizontalDivider(color = Border)
            Field("Server Name", state.name, viewModel::updateName, "My Server")
            Field("MC Version", state.mcVersion, viewModel::updateVersion, "1.21.4")
            Field("Port", state.port.toString(), { viewModel.updatePort(it.toIntOrNull() ?: 25565) }, "25565")
            state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StateCrashed) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                ActionButton("Cancel", ButtonVariant.Secondary, onClick = onCancel)
                ActionButton("Create", ButtonVariant.Primary, onClick = { viewModel.create(onCreated) }, enabled = !state.isCreating)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        OutlinedTextField(value = value, onValueChange = onChange,
            placeholder = { Text(placeholder, color = TextMuted) }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
    }
}
