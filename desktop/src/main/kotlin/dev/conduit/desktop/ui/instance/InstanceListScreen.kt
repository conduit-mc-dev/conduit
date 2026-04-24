package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.InstanceSummary
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InstanceListScreen(
    viewModel: InstanceListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("服务器实例", style = MaterialTheme.typography.headlineSmall)
            TextButton(
                onClick = viewModel::refresh,
                enabled = !state.isLoading,
            ) {
                Text("刷新")
            }
        }

        Spacer(Modifier.height(16.dp))

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        when {
            state.isLoading && state.instances.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.instances.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无服务器实例",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.instances, key = { it.id }) { instance ->
                        InstanceCard(instance)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstanceCard(instance: InstanceSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                instance.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "MC ${instance.mcVersion} · 端口 ${instance.mcPort}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val (color, label) = when (instance.state) {
                InstanceState.RUNNING -> MaterialTheme.colorScheme.primary to "运行中"
                InstanceState.STARTING -> MaterialTheme.colorScheme.tertiary to "启动中"
                InstanceState.STOPPING -> MaterialTheme.colorScheme.tertiary to "停止中"
                InstanceState.INITIALIZING -> MaterialTheme.colorScheme.tertiary to "初始化"
                InstanceState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant to "已停止"
            }
            SuggestionChip(
                onClick = {},
                label = { Text(label) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = color,
                ),
            )
        }
    }
}
