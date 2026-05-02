package dev.conduit.desktop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.conduit.core.model.InstanceState

@Composable
fun StatusChip(
    instanceState: InstanceState,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when (instanceState) {
        InstanceState.RUNNING -> MaterialTheme.colorScheme.tertiary to "● 运行中"
        InstanceState.STARTING -> MaterialTheme.colorScheme.primary to "● 启动中"
        InstanceState.STOPPING -> MaterialTheme.colorScheme.secondary to "● 停止中"
        InstanceState.INITIALIZING -> MaterialTheme.colorScheme.secondary to "● 初始化"
        InstanceState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant to "○ 已停止"
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = color),
        modifier = modifier,
    )
}
