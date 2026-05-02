package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.MinecraftVersion
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateInstanceScreen(
    onCreated: () -> Unit,
    viewModel: CreateInstanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.widthIn(max = 480.dp).padding(24.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("创建服务器实例", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("实例名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCreating,
                )

                VersionDropdown(
                    versions = state.versions,
                    selected = state.selectedVersion,
                    onSelect = viewModel::selectVersion,
                    isLoading = state.isLoadingVersions,
                    error = state.versionsError,
                    enabled = !state.isCreating,
                    onRetry = viewModel::loadVersions,
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = { Text("描述（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCreating,
                )

                OutlinedTextField(
                    value = state.mcPort,
                    onValueChange = { viewModel.updateMcPort(it.filter { c -> c.isDigit() }) },
                    label = { Text("端口（可选，默认自动分配）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isCreating,
                )

                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.createInstance(onCreated) },
                        enabled = !state.isCreating && state.name.isNotBlank() && state.selectedVersion != null,
                    ) {
                        if (state.isCreating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("创建")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionDropdown(
    versions: List<MinecraftVersion>,
    selected: MinecraftVersion?,
    onSelect: (MinecraftVersion) -> Unit,
    isLoading: Boolean,
    error: String?,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    when {
        isLoading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("加载版本列表...", style = MaterialTheme.typography.bodySmall)
            }
        }
        error != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
        else -> {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (enabled) expanded = it },
            ) {
                OutlinedTextField(
                    value = selected?.id ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("MC 版本") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    enabled = enabled,
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    versions.forEach { version ->
                        DropdownMenuItem(
                            text = { Text(version.id) },
                            onClick = {
                                onSelect(version)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}
