package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import dev.conduit.desktop.ui.components.CommandInput
import dev.conduit.desktop.ui.components.ConsoleArea
import dev.conduit.desktop.ui.components.StatusChip
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InstanceDetailScreen(
    instanceId: String,
    onEditProperties: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: InstanceDetailViewModel = koinViewModel { parametersOf(instanceId) },
) {
    val state by viewModel.state.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            onDeleted()
        }
    }

    if (state.showEulaDialog) {
        EulaDialog(
            onAccept = viewModel::acceptEula,
            onDismiss = viewModel::dismissEulaDialog,
        )
    }

    val instance = state.instance
    if (showDeleteConfirm && instance != null) {
        DeleteConfirmDialog(
            instanceName = instance.name,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteInstance()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        HeaderBar(
            state = state,
            onEditProperties = onEditProperties,
            onStart = viewModel::startServer,
            onStop = viewModel::stopServer,
            onKill = viewModel::killServer,
            onRetryDownload = viewModel::retryDownload,
            onDelete = { showDeleteConfirm = true },
        )

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
            state.isLoading && state.instance == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.instance == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "实例未找到",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val inst = state.instance
                if (inst != null && inst.state == InstanceState.RUNNING) {
                    PlayerBar(
                        playerCount = inst.playerCount,
                        maxPlayers = inst.maxPlayers,
                        playerNames = state.playerNames,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // 控制台区域
                ConsoleArea(
                    lines = state.consoleLines,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // 命令输入
                CommandInput(
                    value = state.commandInput,
                    onValueChange = viewModel::updateCommandInput,
                    onSend = viewModel::sendCommand,
                    enabled = state.instance?.state == InstanceState.RUNNING,
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    state: InstanceDetailUiState,
    onEditProperties: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKill: () -> Unit,
    onRetryDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.instance?.let { instance ->
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                StatusChip(instance.state)
            }
        }

        state.instance?.let { instance ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onEditProperties) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("配置")
                }
                ActionButtons(
                    instanceState = instance.state,
                    statusMessage = instance.statusMessage,
                    isActionInProgress = state.isActionInProgress,
                    onStart = onStart,
                    onStop = onStop,
                    onKill = onKill,
                    onRetryDownload = onRetryDownload,
                )
                if (instance.state == InstanceState.STOPPED) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !state.isActionInProgress,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    instanceState: InstanceState,
    statusMessage: String?,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKill: () -> Unit,
    onRetryDownload: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (instanceState) {
            InstanceState.STOPPED -> {
                if (statusMessage?.startsWith("Initialization failed") == true) {
                    Button(
                        onClick = onRetryDownload,
                        enabled = !isActionInProgress,
                    ) {
                        Text("重新下载")
                    }
                }
                Button(
                    onClick = onStart,
                    enabled = !isActionInProgress,
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("启动")
                }
            }
            InstanceState.STARTING -> {
                Button(onClick = {}, enabled = false) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("启动中...")
                }
            }
            InstanceState.RUNNING -> {
                Button(
                    onClick = onStop,
                    enabled = !isActionInProgress,
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("停止")
                }
                TextButton(
                    onClick = onKill,
                    enabled = !isActionInProgress,
                ) {
                    Text("强制停止")
                }
            }
            InstanceState.STOPPING -> {
                Button(onClick = {}, enabled = false) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("停止中...")
                }
                TextButton(
                    onClick = onKill,
                    enabled = !isActionInProgress,
                ) {
                    Text("强制停止")
                }
            }
            InstanceState.INITIALIZING -> {
                // 初始化中不显示操作按钮
            }
            InstanceState.CRASHED -> {
                Button(
                    onClick = onRetryDownload,
                    enabled = !isActionInProgress,
                ) {
                    Text("重新下载")
                }
                Button(
                    onClick = onStart,
                    enabled = !isActionInProgress,
                ) {
                    Text("重启")
                }
            }
        }
    }
}

@Composable
private fun EulaDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Minecraft EULA") },
        text = {
            Text("启动 Minecraft 服务器前，您需要同意 Minecraft 最终用户许可协议 (EULA)。\n\nhttps://aka.ms/MinecraftEULA\n\n点击「接受」即表示您同意该协议条款。")
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("接受")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    instanceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Text("确定要删除实例「$instanceName」吗？\n\n此操作不可撤销，实例的所有文件（包括 world、mod、配置）将被永久删除。")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun PlayerBar(
    playerCount: Int,
    maxPlayers: Int,
    playerNames: List<String>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$playerCount/$maxPlayers 在线",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (playerNames.isNotEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = playerNames.take(5).joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
