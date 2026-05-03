package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstalledMod
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ModsTab(
    instanceId: String,
    daemonId: String,
    viewModel: ModsTabViewModel = koinViewModel { parametersOf(instanceId, daemonId) },
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::updateSearch,
                placeholder = "Search mods...",
                modifier = Modifier.weight(1f),
            )
            ActionButton("Install from Modrinth", ButtonVariant.Primary, onClick = { /* TODO */ })
            ActionButton("Upload .jar", ButtonVariant.Secondary, onClick = { /* TODO */ })
        }
        Spacer(Modifier.height(12.dp))

        // Filter chips + count
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModFilter.entries.forEach { filter ->
                ActionButton(
                    filter.name.lowercase().replaceFirstChar { it.uppercase() },
                    if (state.filter == filter) ButtonVariant.Primary else ButtonVariant.Secondary,
                    onClick = { viewModel.setFilter(filter) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${state.filteredMods.size} mods",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Mod list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.filteredMods, key = { it.id }) { mod ->
                ModCard(
                    mod = mod,
                    onToggle = { viewModel.toggleMod(mod.id, it) },
                    onRemove = { viewModel.removeMod(mod.id) },
                )
            }
            if (state.filteredMods.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "No mods found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModCard(
    mod: InstalledMod,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (mod.enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AccentPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = AccentPurple,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(mod.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "${mod.source} · ${mod.version}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        Switch(
            checked = mod.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
        )
    }
}
