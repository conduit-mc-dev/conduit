package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.*
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

data class InstanceDetailUiStateV2(
    val instance: InstanceSummary? = null,
    val selectedTab: String = "console",
    val consoleLines: List<String> = emptyList(),
    val commandInput: String = "",
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false,
    val playerNames: List<String> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val eulaAccepted: Boolean? = null,
    val showEulaDialog: Boolean = false,
)

class InstanceDetailViewModelV2(
    private val instanceId: String,
    private val daemonId: String,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private var playerPollJob: Job? = null
    private val session get() = daemonManager.getSession(daemonId)
    private val apiClient get() = session?.getApi() ?: error("No session for daemon $daemonId")

    private val _state = MutableStateFlow(InstanceDetailUiStateV2())
    val state: StateFlow<InstanceDetailUiStateV2> = _state

    init {
        session?.let {
            _state.value = _state.value.copy(
                consoleLines = it.getConsoleLines(instanceId).value,
            )
        }
        loadInstance()
        connectWebSocket()
    }

    fun selectTab(tabId: String) {
        _state.value = _state.value.copy(selectedTab = tabId)
    }

    fun loadInstance() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val instance = apiClient.getInstance(instanceId)
                val eula = apiClient.getEula(instanceId)
                _state.value = _state.value.copy(
                    instance = instance,
                    eulaAccepted = eula.accepted,
                    isLoading = false,
                )
                if (instance.state == InstanceState.RUNNING) {
                    refreshPlayerNames()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load: ${e.message}",
                )
            }
        }
    }

    private fun connectWebSocket() {
        val ws = session?.wsClient ?: return
        viewModelScope.launch {
            delay(500)
            ws.subscribe(instanceId)
        }
        viewModelScope.launch {
            ws.messages.collect { msg ->
                if (msg.instanceId != instanceId) return@collect
                when (msg.type) {
                    WsMessage.CONSOLE_OUTPUT -> {
                        try {
                            val payload = json.decodeFromJsonElement<ConsoleOutputPayload>(msg.payload)
                            session?.appendConsoleLine(instanceId, payload.line)
                            _state.value = _state.value.copy(
                                consoleLines = session?.getConsoleLines(instanceId)?.value
                                    ?: emptyList(),
                            )
                        } catch (_: Exception) {}
                    }

                    WsMessage.STATE_CHANGED -> {
                        try {
                            val payload = json.decodeFromJsonElement<StateChangedPayload>(msg.payload)
                            _state.value = _state.value.copy(
                                instance = _state.value.instance?.copy(
                                    state = payload.newState,
                                ),
                            )
                            if (payload.newState == InstanceState.RUNNING) {
                                startPlayerPolling()
                            } else {
                                stopPlayerPolling()
                            }
                        } catch (_: Exception) {}
                    }

                    WsMessage.PLAYERS_CHANGED -> {
                        try {
                            val payload = json.decodeFromJsonElement<PlayersChangedPayload>(msg.payload)
                            _state.value = _state.value.copy(
                                instance = _state.value.instance?.copy(
                                    playerCount = payload.playerCount,
                                    maxPlayers = payload.maxPlayers,
                                ),
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun startPlayerPolling() {
        playerPollJob?.cancel()
        playerPollJob = viewModelScope.launch {
            refreshPlayerNames()
            while (isActive) {
                delay(30_000)
                refreshPlayerNames()
            }
        }
    }

    private fun stopPlayerPolling() {
        playerPollJob?.cancel()
        playerPollJob = null
        _state.value = _state.value.copy(playerNames = emptyList())
    }

    private suspend fun refreshPlayerNames() {
        try {
            _state.value = _state.value.copy(
                playerNames = apiClient.getServerStatus(instanceId).players,
            )
        } catch (_: Exception) {}
    }

    fun startServer() {
        if (_state.value.eulaAccepted != true) {
            _state.value = _state.value.copy(showEulaDialog = true)
            return
        }
        performAction { apiClient.startServer(instanceId) }
    }

    fun stopServer() {
        performAction { apiClient.stopServer(instanceId) }
    }

    fun killServer() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                apiClient.killServer(instanceId)
                loadInstance()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Kill failed: ${e.message}",
                )
            } finally {
                _state.value = _state.value.copy(isActionInProgress = false)
            }
        }
    }

    fun deleteInstance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isActionInProgress = true,
                showDeleteDialog = false,
            )
            try {
                apiClient.deleteInstance(instanceId)
                _state.value = _state.value.copy(isDeleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Delete failed: ${e.message}",
                )
            } finally {
                _state.value = _state.value.copy(isActionInProgress = false)
            }
        }
    }

    fun cancelTask() {
        // TODO: wire up when task cancellation API is available
    }

    fun setShowDeleteDialog(show: Boolean) {
        _state.value = _state.value.copy(showDeleteDialog = show)
    }

    fun acceptEula() {
        viewModelScope.launch {
            try {
                apiClient.acceptEula(instanceId)
                _state.value = _state.value.copy(
                    eulaAccepted = true,
                    showEulaDialog = false,
                )
                startServer()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "EULA accept failed: ${e.message}",
                )
            }
        }
    }

    fun dismissEulaDialog() {
        _state.value = _state.value.copy(showEulaDialog = false)
    }

    fun updateCommandInput(input: String) {
        _state.value = _state.value.copy(commandInput = input)
    }

    fun sendCommand() {
        val command = _state.value.commandInput.trim()
        if (command.isEmpty()) return
        viewModelScope.launch {
            try {
                apiClient.sendCommand(instanceId, command)
                session?.appendConsoleLine(instanceId, "> $command")
                _state.value = _state.value.copy(
                    consoleLines = session?.getConsoleLines(instanceId)?.value
                        ?: emptyList(),
                    commandInput = "",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Command failed: ${e.message}",
                )
            }
        }
    }

    private fun performAction(action: suspend () -> ServerStatusResponse) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                val status = action()
                _state.value = _state.value.copy(
                    instance = _state.value.instance?.copy(state = status.state),
                    isActionInProgress = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "${e.message}",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerPollJob?.cancel()
    }
}
