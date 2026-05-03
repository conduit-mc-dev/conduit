package dev.conduit.desktop.ui.instance

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun InstanceDetailTabScreen(
    instanceId: String,
    daemonId: String,
    state: InstanceDetailUiStateV2,
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
                    instanceState = inst.state,
                    isActionInProgress = state.isActionInProgress,
                    onStart = onStart,
                    onStop = onStop,
                    onKill = onKill,
                    onDelete = onDelete,
                    onCancel = onCancel,
                )

                if (inst.state == InstanceState.INITIALIZING) {
                    InstallProgressScreen(instanceId = instanceId, daemonId = daemonId)
                } else {
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

                    state.error?.let {
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
