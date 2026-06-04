package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.MinecraftVersion
import dev.conduit.desktop.ui.theme.*

/**
 * Version selector matching design mockup S16.
 * Shows a clickable select field + quick-select chips for popular versions.
 */
@Composable
fun VersionSelectField(
    versions: List<MinecraftVersion>,
    selectedVersion: String,
    onVersionSelected: (String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chipShape = RoundedCornerShape(6.dp)

    // Filter to release versions for chips, show all in dropdown
    val releaseVersions = remember(versions) {
        versions.filter { it.type == "release" }
    }
    val chipVersions = remember(releaseVersions) {
        releaseVersions.take(4).map { it.id }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Select field
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Surface)
                .border(1.dp, if (expanded) AccentBlue else Border, RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedVersion.ifBlank { "Select version..." },
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

        // Dropdown
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
                    text = { Text("Loading versions...", color = TextSecondary) },
                    onClick = {},
                )
            } else {
                releaseVersions.forEach { version ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                version.id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (version.id == selectedVersion) AccentBlue else TextPrimary,
                            )
                        },
                        onClick = {
                            onVersionSelected(version.id)
                            expanded = false
                        },
                    )
                }
                if (releaseVersions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No versions available", color = TextSecondary) },
                        onClick = {},
                    )
                }
            }
        }

        // Quick-select chips
        if (chipVersions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chipVersions.forEach { version ->
                    val isSelected = version == selectedVersion
                    Box(
                        modifier = Modifier
                            .clip(chipShape)
                            .background(if (isSelected) AccentBlue.copy(alpha = 0.08f) else Background)
                            .border(
                                1.dp,
                                if (isSelected) AccentBlue else Border,
                                chipShape,
                            )
                            .clickable { onVersionSelected(version) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            version,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) AccentBlue else TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
