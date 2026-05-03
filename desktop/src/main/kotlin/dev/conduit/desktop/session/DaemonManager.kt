package dev.conduit.desktop.session

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.WsConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path

class DaemonManager(
    private val configDir: Path = Path.of(System.getProperty("user.home"), ".conduit"),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessionFile get() = configDir.resolve("session.json").toFile()

    private val _sessions = MutableStateFlow<List<DaemonSession>>(emptyList())
    val sessions: StateFlow<List<DaemonSession>> = _sessions.asStateFlow()

    fun addDaemon(
        daemonId: String,
        daemonName: String,
        daemonUrl: String,
        token: String,
    ): DaemonSession {
        val apiClient = ConduitApiClient(daemonUrl)
        val session = DaemonSession(daemonId, daemonName, daemonUrl, apiClient)
        session.start(token, scope)
        _sessions.value = _sessions.value + session
        return session
    }

    fun removeDaemon(daemonId: String) {
        _sessions.value.find { it.daemonId == daemonId }?.stop()
        _sessions.value = _sessions.value.filter { it.daemonId != daemonId }
    }

    fun getSession(daemonId: String): DaemonSession? =
        _sessions.value.find { it.daemonId == daemonId }

    fun getPrimarySession(): DaemonSession? = _sessions.value.firstOrNull()

    // --- Persistence (single-daemon for now, multi-daemon later) ---

    fun saveSession(daemonUrl: String, token: String, daemonId: String, daemonName: String) {
        sessionFile.parentFile.mkdirs()
        val data = SavedSession(daemonUrl, token, daemonId, daemonName)
        sessionFile.writeText(json.encodeToString(SavedSession.serializer(), data))
    }

    fun loadSavedSession(): SavedSession? {
        if (!sessionFile.exists()) return null
        return try {
            val data = json.decodeFromString(SavedSession.serializer(), sessionFile.readText())
            if (data.token.isBlank() || data.daemonUrl.isBlank()) null else data
        } catch (_: Exception) { null }
    }

    fun clearSession() {
        sessionFile.delete()
    }

    @Serializable
    data class SavedSession(
        val daemonUrl: String,
        val token: String,
        val daemonId: String = "",
        val daemonName: String = "",
    )
}
