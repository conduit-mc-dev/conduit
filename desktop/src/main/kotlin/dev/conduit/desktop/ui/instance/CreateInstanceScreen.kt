package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.AvailableLoader
import dev.conduit.desktop.ui.components.ActionButton
import dev.conduit.desktop.ui.components.ButtonVariant
import dev.conduit.desktop.ui.components.VersionSelectField
import dev.conduit.desktop.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CreateInstanceScreen(
    daemonId: String,
    onCreated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateInstanceViewModel = koinViewModel { parametersOf(daemonId) },
) {
    val state by viewModel.state.collectAsState()

    CreateInstanceScreenContent(
        state = state,
        onNameChange = viewModel::updateName,
        onMcVersionChange = viewModel::updateMcVersion,
        onPortChange = viewModel::updatePort,
        onMaxPlayersChange = viewModel::updateMaxPlayers,
        onLoaderTypeChange = viewModel::updateLoaderType,
        onLoaderVersionChange = viewModel::updateLoaderVersion,
        onCreated = { viewModel.create(onCreated) },
        onCancel = onCancel,
    )
}

/**
 * Pure content composable for CreateInstanceScreen — accepts state directly,
 * no ViewModel or DI. Used by both production screen and UI tests.
 */
@Composable
internal fun CreateInstanceScreenContent(
    state: CreateInstanceState,
    onNameChange: (String) -> Unit = {},
    onMcVersionChange: (String) -> Unit = {},
    onPortChange: (String) -> Unit = {},
    onMaxPlayersChange: (String) -> Unit = {},
    onLoaderTypeChange: (LoaderDisplayType) -> Unit = {},
    onLoaderVersionChange: (String) -> Unit = {},
    onCreated: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Background).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.width(460.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                // Icon box
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("Create Server", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    if (state.daemonName.isNotBlank()) {
                        Text("on ${state.daemonName}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            // ── Server Name ──
            FormField("Server Name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    placeholder = { Text("e.g. Survival SMP", color = TextMuted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Minecraft Version ──
            FormField("Minecraft Version") {
                VersionSelectField(
                    versions = state.mcVersions,
                    selectedVersion = state.mcVersion,
                    onVersionSelected = onMcVersionChange,
                    isLoading = state.versionsLoading,
                )
            }

            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 22.dp))

            // ── Mod Loader ──
            FormField("Mod Loader") {
                LoaderSection(
                    selectedType = state.loaderType,
                    onTypeSelected = onLoaderTypeChange,
                    selectedVersion = state.selectedLoaderVersion,
                    onVersionSelected = onLoaderVersionChange,
                    availableLoaders = state.availableLoaders,
                    isLoading = state.loaderVersionsLoading,
                )
            }

            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 22.dp))

            // ── Port + Max Players (side by side) ──
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormField("Server Port", Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.port.toString(),
                        onValueChange = onPortChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                }
                FormField("Max Players", Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.maxPlayers.toString(),
                        onValueChange = onMaxPlayersChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }

            // ── Error ──
            state.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = StateCrashed)
            }

            // ── Actions ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                ActionButton("Cancel", ButtonVariant.Default, onClick = onCancel)
                ActionButton(
                    "Create Server",
                    ButtonVariant.Success,
                    onClick = onCreated,
                    enabled = !state.isCreating,
                )
            }
        }
    }
}

// ── Private helpers ──

@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        content()
    }
}

/**
 * Loader section: tab selector (NeoForge/Fabric/Quilt/Forge/Vanilla) + version dropdown.
 */
@Composable
private fun LoaderSection(
    selectedType: LoaderDisplayType,
    onTypeSelected: (LoaderDisplayType) -> Unit,
    selectedVersion: String,
    onVersionSelected: (String) -> Unit,
    availableLoaders: List<AvailableLoader>,
    isLoading: Boolean,
) {
    val loaderTypes = listOf(
        LoaderDisplayType.NEOFORGE,
        LoaderDisplayType.FABRIC,
        LoaderDisplayType.QUILT,
        LoaderDisplayType.FORGE,
        LoaderDisplayType.VANILLA,
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tab row
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    drawLine(Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .padding(bottom = 1.dp),
        ) {
            loaderTypes.forEach { type ->
                val isSelected = type == selectedType
                Box(
                    modifier = Modifier.clickable { onTypeSelected(type) }
                        .drawBehind {
                            if (isSelected) {
                                drawLine(AccentBlue, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(
                        type.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) AccentBlue else TextSecondary,
                    )
                }
            }
        }

        // Version dropdown (hidden for Vanilla)
        if (selectedType != LoaderDisplayType.VANILLA) {
            LoaderVersionDropdown(
                selectedType = selectedType,
                selectedVersion = selectedVersion,
                onVersionSelected = onVersionSelected,
                availableLoaders = availableLoaders,
                isLoading = isLoading,
            )
            Text(
                "Auto-selected latest compatible version",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

@Composable
private fun LoaderVersionDropdown(
    selectedType: LoaderDisplayType,
    selectedVersion: String,
    onVersionSelected: (String) -> Unit,
    availableLoaders: List<AvailableLoader>,
    isLoading: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val backendType = CreateInstanceViewModel.toBackendType(selectedType)
    val versions = remember(availableLoaders, backendType) {
        availableLoaders.find { it.type == backendType }?.versions ?: emptyList()
    }

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, if (expanded) AccentBlue else Border, RoundedCornerShape(8.dp))
            .clickable { if (versions.isNotEmpty()) expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    isLoading -> "Loading versions..."
                    selectedVersion.isNotBlank() -> "${selectedType.displayName} $selectedVersion"
                    else -> "Select ${selectedType.displayName} version..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedVersion.isNotBlank()) TextPrimary else TextMuted,
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(4.dp)
            .widthIn(min = 200.dp)
            .defaultMinSize(minWidth = 300.dp),
    ) {
        if (isLoading) {
            DropdownMenuItem(
                text = { Text("Loading...", color = TextSecondary) },
                onClick = {},
            )
        } else {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = {
                        Text(
                            version,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (version == selectedVersion) AccentBlue else TextPrimary,
                        )
                    },
                    onClick = {
                        onVersionSelected(version)
                        expanded = false
                    },
                )
            }
            if (versions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No versions available for this MC version", color = TextSecondary) },
                    onClick = {},
                )
            }
        }
    }
}
