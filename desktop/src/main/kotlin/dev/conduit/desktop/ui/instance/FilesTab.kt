package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val PROTECTED = setOf("server.jar", "libraries", "versions")

@Composable
fun FilesTab(
    instanceId: String,
    daemonId: String,
    viewModel: FilesTabViewModel = koinViewModel { parametersOf(instanceId, daemonId) },
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        // Breadcrumb + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val parts = state.currentPath.split("/").filter { it.isNotEmpty() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "root",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentBlue,
                    modifier = Modifier.clickable { viewModel.loadFiles("") },
                )
                parts.forEachIndexed { i, part ->
                    Text(
                        " / ",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Text(
                        part,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (i == parts.lastIndex) TextPrimary else AccentBlue,
                        modifier = if (i < parts.lastIndex) {
                            Modifier.clickable { viewModel.navigateToBreadcrumb(i) }
                        } else {
                            Modifier
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ActionButton("Upload", ButtonVariant.Secondary, onClick = { /* TODO */ })
            ActionButton("New Folder", ButtonVariant.Secondary, onClick = { /* TODO */ })
        }
        Spacer(Modifier.height(12.dp))

        // File list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(state.entries, key = { it.name }) { entry ->
                val isProtected = entry.name in PROTECTED
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .alpha(if (isProtected) 0.5f else 1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .then(
                            if (entry.type == "directory") {
                                Modifier.clickable { viewModel.navigateToFolder(entry.name) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        if (entry.type == "directory") Icons.Default.Folder
                        else Icons.AutoMirrored.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.type == "directory") StateInstalling else TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        entry.name,
                        style = if (entry.type == "directory")
                            MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        else MaterialTheme.typography.bodySmall,
                        color = if (entry.type == "directory") AccentBlue else if (isProtected) TextSecondary else TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (isProtected) {
                        Text(
                            "Protected",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StateCrashed,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StateCrashed.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    val fileSize = entry.size
                    if (entry.type != "directory" && fileSize != null) {
                        Text(
                            formatSize(fileSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1048576.0)} MB"
}
