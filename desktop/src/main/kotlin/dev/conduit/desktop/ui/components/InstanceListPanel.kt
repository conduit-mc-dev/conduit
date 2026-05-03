package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceSummary
import dev.conduit.core.model.WsConnectionState
import dev.conduit.desktop.ui.theme.*

data class DaemonGroup(
    val daemonId: String,
    val daemonName: String,
    val connectionState: WsConnectionState,
    val instances: List<InstanceSummary>,
)

@Composable
fun InstanceListPanel(
    daemonGroups: List<DaemonGroup>,
    selectedInstanceId: String?,
    onInstanceClick: (daemonId: String, instanceId: String) -> Unit,
    onCreateInstance: (daemonId: String) -> Unit,
    onPairDaemon: () -> Unit,
    onDaemonEdit: (daemonId: String) -> Unit,
    onDaemonDisconnect: (daemonId: String) -> Unit,
    onDaemonForget: (daemonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier.width(220.dp).fillMaxHeight().background(Background)
            .border(1.dp, Border),
    ) {
        SearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, modifier = Modifier.padding(10.dp))
        HorizontalDivider(color = Border)

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val hasResults = searchQuery.isBlank() || daemonGroups.any { group ->
                group.instances.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
            daemonGroups.forEach { group ->
                item(key = "header-${group.daemonId}") {
                    DaemonGroupHeader(
                        group = group,
                        onEdit = { onDaemonEdit(group.daemonId) },
                        onDisconnect = { onDaemonDisconnect(group.daemonId) },
                        onForget = { onDaemonForget(group.daemonId) },
                    )
                }
                val filtered = if (searchQuery.isBlank()) group.instances
                else group.instances.filter { it.name.contains(searchQuery, ignoreCase = true) }
                items(filtered, key = { it.id }) { instance ->
                    ConduitCard(
                        instance = instance,
                        isSelected = instance.id == selectedInstanceId,
                        onClick = { onInstanceClick(group.daemonId, instance.id) },
                        dimmed = group.connectionState != WsConnectionState.CONNECTED,
                    )
                }
                item(key = "tail-${group.daemonId}") {
                    NewServerTailCard(
                        onClick = { onCreateInstance(group.daemonId) },
                        enabled = group.connectionState == WsConnectionState.CONNECTED,
                    )
                }
            }
            if (!hasResults) {
                item(key = "search-empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Elevated, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No servers matching", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
                        Text(searchQuery, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        HorizontalDivider(color = Border)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPairDaemon).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Link, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Pair Daemon", style = MaterialTheme.typography.bodySmall, color = AccentBlue)
        }
    }
}

@Composable
private fun DaemonGroupHeader(
    group: DaemonGroup,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(Background).padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DaemonStatusDot(group.connectionState, StatusDotSize.Small)
        Spacer(Modifier.width(6.dp))
        Text(group.daemonName.uppercase(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.width(4.dp))
        val statusText = when (group.connectionState) {
            WsConnectionState.CONNECTED -> "online"
            WsConnectionState.RECONNECTING, WsConnectionState.CONNECTING -> "reconnecting"
            WsConnectionState.DISCONNECTED -> "offline"
        }
        Text(statusText, style = MaterialTheme.typography.labelSmall, color = when (group.connectionState) {
            WsConnectionState.CONNECTED -> DaemonOnline
            WsConnectionState.RECONNECTING, WsConnectionState.CONNECTING -> DaemonReconnecting
            WsConnectionState.DISCONNECTED -> DaemonOffline
        })
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { dropdownExpanded = true }, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Daemon settings", tint = TextSecondary, modifier = Modifier.size(12.dp))
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
                    .padding(4.dp)
                    .widthIn(min = 150.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Edit", style = MaterialTheme.typography.bodySmall, color = TextPrimary) },
                    onClick = { dropdownExpanded = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                )
                HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 2.dp))
                DropdownMenuItem(
                    text = { Text("Disconnect", style = MaterialTheme.typography.bodySmall, color = TextPrimary) },
                    onClick = { dropdownExpanded = false; onDisconnect() },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                )
                DropdownMenuItem(
                    text = { Text("Forget", style = MaterialTheme.typography.bodySmall, color = ButtonDanger) },
                    onClick = { dropdownExpanded = false; onForget() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ButtonDanger, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun NewServerTailCard(onClick: () -> Unit, enabled: Boolean) {
    val dashColor = Border
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
            .drawBehind {
                val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                drawRoundRect(color = dashColor, style = stroke, cornerRadius = CornerRadius(10.dp.toPx()), size = Size(size.width, size.height))
            }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text("New Server", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}
