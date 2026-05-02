package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ServerPropertiesScreen(
    instanceId: String,
    onBack: () -> Unit,
    viewModel: ServerPropertiesViewModel = koinViewModel { parametersOf(instanceId) },
) {
    val state by viewModel.state.collectAsState()

    if (state.saveSuccess) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSuccess,
            title = { Text("保存成功") },
            text = {
                val msg = if (state.restartRequired) {
                    "配置已保存。部分修改需要重启服务器才能生效。"
                } else {
                    "配置已保存。"
                }
                Text(msg)
            },
            confirmButton = {
                Button(onClick = viewModel::dismissSuccess) {
                    Text("确定")
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Header
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
                Text(
                    text = "server.properties",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = state.editedValues.isNotEmpty() && !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("保存")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Error
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Content
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.properties.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "无法加载配置",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.properties.entries.toList(), key = { it.key }) { (key, original) ->
                        val displayValue = state.editedValues[key] ?: original
                        val isModified = key in state.editedValues
                        PropertyRow(
                            keyName = key,
                            value = displayValue,
                            isModified = isModified,
                            onValueChange = { newValue ->
                                viewModel.updateValue(key, newValue)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyRow(
    keyName: String,
    value: String,
    isModified: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = keyName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(160.dp),
            color = if (isModified) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
    }
}
