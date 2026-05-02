package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.conduit.core.model.InstanceState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InstanceDetailScreen(
    instanceId: String,
    onBack: () -> Unit,
    onEditProperties: () -> Unit,
    viewModel: InstanceDetailViewModel = koinViewModel { parametersOf(instanceId) },
) {
    val state by viewModel.state.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) {
            onBack()
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
            onBack = onBack,
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
    onBack: () -> Unit,
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
            TextButton(onClick = onBack) {
                Text("← 返回")
            }
            state.instance?.let { instance ->
                Text(
                    text = instance.name,
                    style = MaterialTheme.typography.headlineSmall,
                )
                StatusChip(instance.state)
            }
        }

        state.instance?.let { instance ->
            TextButton(onClick = onEditProperties) {
                Text("⚙ 配置")
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

@Composable
private fun StatusChip(instanceState: InstanceState) {
    val (color, label) = when (instanceState) {
        InstanceState.RUNNING -> MaterialTheme.colorScheme.primary to "运行中"
        InstanceState.STARTING -> MaterialTheme.colorScheme.tertiary to "启动中"
        InstanceState.STOPPING -> MaterialTheme.colorScheme.tertiary to "停止中"
        InstanceState.INITIALIZING -> MaterialTheme.colorScheme.tertiary to "初始化"
        InstanceState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant to "已停止"
    }
    SuggestionChip(
        onClick = {},
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = color),
    )
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
        }
    }
}

@Composable
private fun ConsoleArea(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "控制台输出将显示在此处",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && enabled) {
                        onSend()
                        true
                    } else {
                        false
                    }
                },
            placeholder = { Text("输入命令...") },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Button(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
        ) {
            Text("发送")
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
