package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.InstanceSummary
import dev.conduit.desktop.ui.theme.*

@Composable
fun ConduitCard(
    instance: InstanceSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        isSelected && instance.state == InstanceState.CRASHED -> StateCrashed.copy(alpha = 0.4f)
        isSelected -> AccentBlue.copy(alpha = 0.4f)
        else -> Border
    }
    val bgColor = Surface

    Box(
        modifier = modifier
            .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = instance.name,
                    style = if (isSelected) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(700))
                    else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight(600)),
                    color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusDot(instance.state, StatusDotSize.Small)
            }
            val infoText = buildString {
                instance.loader?.let { append("${it.type.name.lowercase().replaceFirstChar { c -> c.uppercase() }} ") }
                append(instance.mcVersion)
                if (instance.state == InstanceState.RUNNING) append(" · ${instance.playerCount}/${instance.maxPlayers}")
            }
            Text(
                text = infoText,
                style = MaterialTheme.typography.labelMedium,
                color = when (instance.state) {
                    InstanceState.RUNNING -> StateRunning
                    InstanceState.STARTING -> StateStarting
                    InstanceState.STOPPING -> StateStopping
                    InstanceState.CRASHED -> StateCrashed
                    InstanceState.STOPPED -> TextMuted
                    InstanceState.INITIALIZING -> StateInstalling
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // Bottom progress bar for INSTALLING
        if (instance.state == InstanceState.INITIALIZING) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.5f) // TODO: wire real progress
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(Brush.horizontalGradient(listOf(ProgressInstallingStart, ProgressInstallingEnd))),
            )
        }
    }
}
