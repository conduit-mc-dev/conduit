package dev.conduit.desktop.session

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitWsClient
import dev.conduit.core.model.WsConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DaemonSession(
    val daemonId: String,
    val daemonName: String,
    val daemonUrl: String,
    private val apiClient: ConduitApiClient,
) {
    private var _wsClient: ConduitWsClient? = null
    val wsClient: ConduitWsClient
        get() = _wsClient ?: error("Session not started for daemon $daemonId")

    val isActive: Boolean get() = _wsClient != null

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private val consoleBuffers = mutableMapOf<String, MutableStateFlow<List<String>>>()

    fun start(token: String, scope: CoroutineScope): ConduitWsClient {
        apiClient.setBaseUrl(daemonUrl)
        apiClient.setToken(token)
        val client = ConduitWsClient(baseUrl = daemonUrl, token = token)
        _wsClient = client
        client.connect(scope)
        scope.launch {
            client.connectionState.collect { _connectionState.value = it }
        }
        return client
    }

    fun getApi(): ConduitApiClient = apiClient

    fun getConsoleLines(instanceId: String): StateFlow<List<String>> {
        return consoleBuffers.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
    }

    fun appendConsoleLine(instanceId: String, line: String) {
        val buffer = consoleBuffers.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        val list = buffer.value.toMutableList()
        list.add(line)
        if (list.size > MAX_CONSOLE_LINES) list.removeAt(0)
        buffer.value = list
    }

    fun stop() {
        _wsClient?.close()
        _wsClient = null
        _connectionState.value = WsConnectionState.DISCONNECTED
        consoleBuffers.clear()
    }

    companion object {
        const val MAX_CONSOLE_LINES = 1000
    }
}
