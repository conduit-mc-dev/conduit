package dev.conduit.desktop.ui.instance

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.WsConnectionState
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun InstanceDetailTabScreen(
    instanceId: String,
    daemonId: String,
    state: InstanceDetailUiState,
    connectionState: WsConnectionState = WsConnectionState.CONNECTED,
    daemonName: String = daemonId,
    onSelectTab: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKill: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onUpdateCommand: (String) -> Unit,
    onSendCommand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDaemonReconnecting = connectionState == WsConnectionState.RECONNECTING || connectionState == WsConnectionState.CONNECTING
    if (state.showDeleteDialog) {
        state.instance?.let {
            DeleteInstanceDialog(it.name, onConfirm = onConfirmDelete, onDismiss = onDismissDelete)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading && state.instance == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.instance == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Instance not found", color = TextSecondary)
                }
            }

            else -> {
                val inst = state.instance

                ContentHeader(
                    instanceName = inst.name,
                    instanceState = if (isDaemonReconnecting) InstanceState.STOPPED else inst.state,
                    isActionInProgress = state.isActionInProgress || isDaemonReconnecting,
                    onStart = onStart,
                    onStop = onStop,
                    onKill = onKill,
                    onDelete = onDelete,
                    onCancel = onCancel,
                )

                if (isDaemonReconnecting) {
                    ReconnectBanner(daemonName = daemonName)
                    TabBar(
                        tabs = listOf(
                            TabItem("console", "Console"),
                            TabItem("mods", "Mods"),
                            TabItem("players", "Players"),
                            TabItem("config", "Config"),
                            TabItem("files", "Files"),
                        ),
                        selectedTabId = state.selectedTab,
                        onTabSelected = onSelectTab,
                    )
                    StaleContent(daemonName = daemonName)
                } else if (inst.state == InstanceState.INITIALIZING) {
                    InstallProgressScreen(instanceId = instanceId, daemonId = daemonId)
                } else {
                    if (inst.state == InstanceState.CRASHED) {
                        CrashBanner(error = state.error)
                    }
                    val tabs = listOf(
                        TabItem("console", "Console"),
                        TabItem("mods", "Mods"),
                        TabItem(
                            "players",
                            "Players",
                            count = if (inst.playerCount > 0) inst.playerCount else null,
                        ),
                        TabItem("config", "Config"),
                        TabItem("files", "Files"),
                    )

                    TabBar(
                        tabs = tabs,
                        selectedTabId = state.selectedTab,
                        onTabSelected = onSelectTab,
                    )

                    state.error?.takeIf { inst.state != InstanceState.CRASHED }?.let {
                        Text(
                            it,
                            color = StateCrashed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Background),
                    ) {
                        AnimatedContent(
                            targetState = state.selectedTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab",
                        ) { tab ->
                            when (tab) {
                                "console" -> ConsoleTab(
                                    lines = state.consoleLines,
                                    commandInput = state.commandInput,
                                    onCommandChange = onUpdateCommand,
                                    onSendCommand = onSendCommand,
                                    isRunning = inst.state == InstanceState.RUNNING,
                                )

                                "players" -> PlayersTab(
                                    playerCount = inst.playerCount,
                                    maxPlayers = inst.maxPlayers,
                                    playerNames = state.playerNames,
                                )

                                "config" -> ConfigTab(
                                    instanceId = instanceId,
                                    daemonId = daemonId,
                                )

                                "mods" -> ModsTab(
                                    instanceId = instanceId,
                                    daemonId = daemonId,
                                )

                                "files" -> FilesTab(
                                    instanceId = instanceId,
                                    daemonId = daemonId,
                                )

                                else -> PlaceholderTab(tab)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(name: String) {
    Box(
        Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$name — coming soon",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

@Composable
private fun CrashBanner(error: String?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StateCrashed.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = StateCrashed,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                "Server crashed",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = StateCrashed,
            )
            if (error != null) {
                Text(
                    error,
                    fontSize = 10.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReconnectBanner(daemonName: String) {
    val transition = rememberInfiniteTransition(label = "reconnectSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1000, easing = LinearEasing)),
        label = "spinAngle",
    )

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(DaemonReconnecting.copy(alpha = 0.06f))
            .drawBehind {
                drawLine(
                    color = DaemonReconnecting.copy(alpha = 0.2f),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Spinning circle: 16dp, border #21262d with border-top #d29922
        Box(
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }
                .drawBehind {
                    drawCircle(color = Border, radius = size.minDimension / 2, style = Stroke(width = 2.dp.toPx()))
                    drawArc(
                        color = DaemonReconnecting,
                        startAngle = -90f, sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        )
        Column {
            Text("Reconnecting to $daemonName...", fontSize = 12.sp, color = DaemonReconnecting)
            Text("Retrying connection", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun StaleContent(daemonName: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Clock-rewind icon approximation using a simple circle + line
                Box(
                    modifier = Modifier.size(40.dp)
                        .drawBehind {
                            drawCircle(color = DaemonReconnecting, radius = size.minDimension / 2, style = Stroke(width = 2.dp.toPx()))
                            drawLine(color = DaemonReconnecting, start = center, end = Offset(center.x, center.y - size.height * 0.3f), strokeWidth = 2.dp.toPx())
                            drawLine(color = DaemonReconnecting, start = center, end = Offset(center.x + size.width * 0.2f, center.y), strokeWidth = 2.dp.toPx())
                        },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Connection lost",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight(700)),
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Data shown may be outdated. Waiting for reconnection to $daemonName...",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
        }
    }
}
