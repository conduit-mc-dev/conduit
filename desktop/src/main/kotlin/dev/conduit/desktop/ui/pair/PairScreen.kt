package dev.conduit.desktop.ui.pair

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PairScreen(
    onPaired: () -> Unit,
    viewModel: PairViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "连接到 Daemon",
                    style = MaterialTheme.typography.headlineSmall,
                )

                when (state.step) {
                    PairStep.CONNECT -> ConnectStep(state, viewModel)
                    PairStep.ENTER_CODE -> EnterCodeStep(state, viewModel, onPaired)
                    PairStep.DONE -> {}
                }

                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectStep(state: PairUiState, viewModel: PairViewModel) {
    OutlinedTextField(
        value = state.daemonUrl,
        onValueChange = viewModel::updateDaemonUrl,
        label = { Text("Daemon 地址") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
    )

    Button(
        onClick = viewModel::connect,
        enabled = !state.isLoading && state.daemonUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text("连接")
    }
}

@Composable
private fun EnterCodeStep(state: PairUiState, viewModel: PairViewModel, onPaired: () -> Unit) {
    state.healthStatus?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    OutlinedTextField(
        value = state.pairCode,
        onValueChange = viewModel::updatePairCode,
        label = { Text("配对码（6 位）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
    )

    OutlinedTextField(
        value = state.deviceName,
        onValueChange = viewModel::updateDeviceName,
        label = { Text("设备名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isLoading,
    )

    Button(
        onClick = { viewModel.confirmPairing(onPaired) },
        enabled = !state.isLoading && state.pairCode.isNotBlank() && state.deviceName.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text("配对")
    }
}
