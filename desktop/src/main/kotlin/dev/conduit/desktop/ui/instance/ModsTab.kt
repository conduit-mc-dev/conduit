package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.core.model.InstalledMod
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Pre-computed color palettes for mod icons (based on name hash). */
private val modIconColors = listOf(
    Color(0xFF1A472A) to Color(0xFF0D2818), // green
    Color(0xFF4A2C0A) to Color(0xFF2D1A06), // amber
    Color(0xFF2A1A47) to Color(0xFF160D28), // purple
    Color(0xFF0A2A4A) to Color(0xFF061A2D), // blue
    Color(0xFF4A0A1A) to Color(0xFF2D0610), // red
    Color(0xFF0A4A3A) to Color(0xFF062D22), // teal
    Color(0xFF3A2A0A) to Color(0xFF221A06), // brown
    Color(0xFF1A0A4A) to Color(0xFF10062D), // indigo
)

@Composable
fun ModsTab(
    instanceId: String,
    daemonId: String,
    viewModel: ModsTabViewModel = koinViewModel { parametersOf(instanceId, daemonId) },
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface)
                .border(1.dp, Border, RoundedCornerShape(0.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::updateSearch,
                placeholder = "Search mods...",
                modifier = Modifier.weight(1f),
            )
            ActionButton("Install from Modrinth", ButtonVariant.Secondary, onClick = { /* TODO */ })
            ActionButton("Upload .jar", ButtonVariant.Secondary, onClick = { /* TODO */ })
        }

        // Filter underline tabs
        FilterTabs(
            filters = ModFilter.entries,
            currentFilter = state.filter,
            mods = state.mods,
            onFilterChange = viewModel::setFilter,
        )

        // Mod list
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
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
private fun FilterTabs(
    filters: List<ModFilter>,
    currentFilter: ModFilter,
    mods: List<InstalledMod>,
    onFilterChange: (ModFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Background)
            .padding(horizontal = 16.dp),
    ) {
        filters.forEach { filter ->
            val isActive = currentFilter == filter
            val count = when (filter) {
                ModFilter.ALL -> mods.size
                ModFilter.ENABLED -> mods.count { it.enabled }
                ModFilter.DISABLED -> mods.count { !it.enabled }
            }
            Column(
                modifier = Modifier.clickable { onFilterChange(filter) }
                    .drawBehind {
                        // Bottom border: 2px blue for active, 1px Border for inactive
                        val strokeWidth = if (isActive) 2.dp.toPx() else 1.dp.toPx()
                        val color = if (isActive) AccentBlue else Border
                        drawLine(
                            color = color,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokeWidth,
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${filter.name.lowercase().replaceFirstChar { it.uppercase() }} ($count)",
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) AccentBlue else TextSecondary,
                )
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
    var menuExpanded by remember { mutableStateOf(false) }
    val colorIndex = (mod.name.hashCode() and 0x7FFFFFFF) % modIconColors.size
    val (gradientTop, gradientBottom) = modIconColors[colorIndex]

    Row(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (mod.enabled) 1f else 0.55f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Colored icon
        Box(
            modifier = Modifier.size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (mod.enabled)
                        Modifier.background(Brush.linearGradient(listOf(gradientTop, gradientBottom)))
                    else
                        Modifier.background(Background).border(1.dp, Border, RoundedCornerShape(8.dp)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                tint = if (mod.enabled) gradientTop.copy(alpha = 0.9f) else TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }

        // Name + source/version
        Column(modifier = Modifier.weight(1f)) {
            Text(mod.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "${mod.source} · ${mod.version}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }

        // Status badge
        StatusBadge(enabled = mod.enabled)

        // Context menu
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (mod.enabled) "Disable" else "Enable") },
                    onClick = { menuExpanded = false; onToggle(!mod.enabled) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove", color = StateCrashed) },
                    onClick = { menuExpanded = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(enabled: Boolean) {
    val (bgColor, textColor, label) = if (enabled) {
        Triple(StateRunning.copy(alpha = 0.1f), StateRunning, "Enabled")
    } else {
        Triple(TextMuted.copy(alpha = 0.15f), TextMuted, "Disabled")
    }
    Text(
        label,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
