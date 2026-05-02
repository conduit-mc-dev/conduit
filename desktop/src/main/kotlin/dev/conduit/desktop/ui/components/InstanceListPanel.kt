package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        Text(
            text = "+ 创建实例",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCreateInstance)
                .padding(8.dp),
        )
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
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                    )
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
            InstanceState.RUNNING -> "● 运行中"
            InstanceState.STARTING -> "● 启动中"
            InstanceState.STOPPING -> "● 停止中"
            InstanceState.INITIALIZING -> "● 初始化"
            InstanceState.STOPPED -> "○ 已停止"
        }
        val statusColor = when (instance.state) {
            InstanceState.RUNNING -> MaterialTheme.colorScheme.tertiary
            InstanceState.STARTING -> MaterialTheme.colorScheme.primary
            InstanceState.STOPPING, InstanceState.INITIALIZING -> MaterialTheme.colorScheme.secondary
            InstanceState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
    }
}
