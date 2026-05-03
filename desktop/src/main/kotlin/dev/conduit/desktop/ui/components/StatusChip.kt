package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState

private val ChipShape = RoundedCornerShape(12.dp)

@Composable
fun StatusChip(
    instanceState: InstanceState,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor, label) = when (instanceState) {
        InstanceState.RUNNING -> Triple(
            MaterialTheme.colorScheme.tertiary,
            Color(0xFF003737),
            "● 运行中",
        )
        InstanceState.STARTING -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.primary,
            "● 启动中",
        )
        InstanceState.STOPPING -> Triple(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.secondary,
            "● 停止中",
        )
        InstanceState.INITIALIZING -> Triple(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.secondary,
            "● 初始化",
        )
        InstanceState.STOPPED -> Triple(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            "○ 已停止",
        )
        InstanceState.CRASHED -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.error,
            "● 已崩溃",
        )
    }
    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
        )
    }
}
