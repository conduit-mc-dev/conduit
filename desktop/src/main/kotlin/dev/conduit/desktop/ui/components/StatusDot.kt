package dev.conduit.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.WsConnectionState
import dev.conduit.desktop.ui.theme.*

enum class StatusDotSize(val dp: Dp) {
    Small(6.dp),
    Medium(8.dp),
    Large(10.dp),
}

@Composable
fun StatusDot(
    instanceState: InstanceState,
    size: StatusDotSize = StatusDotSize.Small,
    modifier: Modifier = Modifier,
) {
    val color = when (instanceState) {
        InstanceState.RUNNING -> StateRunning
        InstanceState.STOPPED -> StateStopped
        InstanceState.STARTING -> StateStarting
        InstanceState.STOPPING -> StateStopping
        InstanceState.CRASHED -> StateCrashed
        InstanceState.INITIALIZING -> StateInstalling
    }
    val hasGlow = instanceState == InstanceState.RUNNING
    val isBreathing = instanceState in setOf(
        InstanceState.STARTING, InstanceState.STOPPING, InstanceState.INITIALIZING,
    )
    StatusDotImpl(color, size, hasGlow, isBreathing, modifier)
}

@Composable
fun DaemonStatusDot(
    connectionState: WsConnectionState,
    size: StatusDotSize = StatusDotSize.Small,
    modifier: Modifier = Modifier,
) {
    val color = when (connectionState) {
        WsConnectionState.CONNECTED -> DaemonOnline
        WsConnectionState.CONNECTING, WsConnectionState.RECONNECTING -> DaemonReconnecting
        WsConnectionState.DISCONNECTED -> DaemonOffline
    }
    val hasGlow = connectionState == WsConnectionState.CONNECTED
    val isBreathing = connectionState == WsConnectionState.RECONNECTING
    StatusDotImpl(color, size, hasGlow, isBreathing, modifier)
}

@Composable
private fun StatusDotImpl(
    color: Color,
    size: StatusDotSize,
    hasGlow: Boolean,
    isBreathing: Boolean,
    modifier: Modifier,
) {
    val alpha = if (isBreathing) {
        val transition = rememberInfiniteTransition(label = "statusBreathing")
        val a by transition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "statusAlpha",
        )
        a
    } else 1.0f

    val dotModifier = modifier
        .size(size.dp)
        .then(
            if (hasGlow) Modifier.shadow(
                elevation = (size.dp.value * 0.6).dp,
                shape = CircleShape,
                ambientColor = color.copy(alpha = 0.4f),
                spotColor = color.copy(alpha = 0.4f),
            ) else Modifier
        )
        .clip(CircleShape)
        .background(color.copy(alpha = alpha))

    Box(dotModifier)
}
