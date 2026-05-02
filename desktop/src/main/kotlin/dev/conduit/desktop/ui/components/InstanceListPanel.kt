package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.InstanceSummary

@Composable
fun InstanceListPanel(
    instances: List<InstanceSummary>,
    selectedInstanceId: String?,
    isLoading: Boolean,
    onInstanceClick: (String) -> Unit,
    onCreateInstance: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = Color(0xFF49454F),
                    start = Offset(size.width - 0.5f, 0f),
                    end = Offset(size.width - 0.5f, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "实例",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onRefresh,
                enabled = !isLoading,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text("刷新", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(6.dp))

        if (isLoading && instances.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(instances, key = { it.id }) { instance ->
                    InstanceListItem(
                        instance = instance,
                        isSelected = instance.id == selectedInstanceId,
                        onClick = { onInstanceClick(instance.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCreateInstance)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "创建实例",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InstanceListItem(
    instance: InstanceSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isSelected) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(
            text = instance.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "MC ${instance.mcVersion} · ${instance.mcPort}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        val statusLabel = when (instance.state) {
            InstanceState.RUNNING -> "运行中"
            InstanceState.STARTING -> "启动中"
            InstanceState.STOPPING -> "停止中"
            InstanceState.INITIALIZING -> "初始化"
            InstanceState.STOPPED -> "已停止"
        }
        val statusColor = when (instance.state) {
            InstanceState.RUNNING -> MaterialTheme.colorScheme.tertiary
            InstanceState.STARTING -> MaterialTheme.colorScheme.primary
            InstanceState.STOPPING, InstanceState.INITIALIZING -> MaterialTheme.colorScheme.secondary
            InstanceState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val isRunning = instance.state != InstanceState.STOPPED
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Circle else Icons.Outlined.Circle,
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                tint = statusColor,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
        if (instance.state == InstanceState.RUNNING && instance.playerCount > 0) {
            Text(
                text = "${instance.playerCount}/${instance.maxPlayers} 在线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
