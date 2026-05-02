package dev.conduit.desktop.session

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitWsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.createDirectories

class SessionManager(
    private val apiClient: ConduitApiClient,
    private val configDir: Path = Path.of(System.getProperty("user.home"), ".conduit"),
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessionFile: java.io.File get() = configDir.resolve("session.json").toFile()

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

    // --- Persistence ---

    fun saveSession(daemonUrl: String, token: String) {
        sessionFile.parentFile.mkdirs()
        val data = SavedSession(daemonUrl, token)
        sessionFile.writeText(json.encodeToString(SavedSession.serializer(), data))
    }

    fun loadSavedSession(): SavedSession? = loadFromDisk(configDir)

    fun clearSession() {
        sessionFile.delete()
    }

    @Serializable
    data class SavedSession(
        val daemonUrl: String,
        val token: String,
    )

    companion object {
        const val MAX_CONSOLE_LINES = 1000

        private val defaultConfigDir = Path.of(System.getProperty("user.home"), ".conduit")

        fun loadFromDisk(configDir: Path = defaultConfigDir): SavedSession? {
            val file = configDir.resolve("session.json").toFile()
            if (!file.exists()) return null
            return try {
                val json = Json { ignoreUnknownKeys = true }
                val data = json.decodeFromString(SavedSession.serializer(), file.readText())
                if (data.token.isBlank() || data.daemonUrl.isBlank()) null
                else data
            } catch (_: Exception) {
                null
            }
        }
    }
}
