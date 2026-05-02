package dev.conduit.desktop.session

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitWsClient
import dev.conduit.core.model.ConsoleOutputPayload
import dev.conduit.core.model.WsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class SessionManager(private val apiClient: ConduitApiClient) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var _wsClient: ConduitWsClient? = null
    val wsClient: ConduitWsClient
        get() = _wsClient ?: error("Session not started")

    val isActive: Boolean get() = _wsClient != null

    private val consoleBuffers = mutableMapOf<String, MutableStateFlow<List<String>>>()

    fun start(token: String): ConduitWsClient {
        val client = ConduitWsClient(
            baseUrl = apiClient.baseUrl,
            token = token,
            json = json,
        )
        _wsClient = client
        return client
    }

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
        consoleBuffers.clear()
    }

    companion object {
        const val MAX_CONSOLE_LINES = 1000
    }
}
