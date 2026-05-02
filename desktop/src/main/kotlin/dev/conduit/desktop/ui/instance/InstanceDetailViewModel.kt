package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitApiException
import dev.conduit.core.model.*
import dev.conduit.desktop.session.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.logging.Logger

data class InstanceDetailUiState(
    val instance: InstanceSummary? = null,
    val eulaAccepted: Boolean? = null,
    val consoleLines: List<String> = emptyList(),
    val commandInput: String = "",
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val showEulaDialog: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false,
    val playerNames: List<String> = emptyList(),
)

class InstanceDetailViewModel(
    private val instanceId: String,
    private val apiClient: ConduitApiClient,
    private val session: SessionManager,
) : ViewModel() {

    private val log = Logger.getLogger(InstanceDetailViewModel::class.java.name)
    private val json = Json { ignoreUnknownKeys = true }
    private var playerPollJob: Job? = null

    private val _state = MutableStateFlow(InstanceDetailUiState())
    val state: StateFlow<InstanceDetailUiState> = _state

    init {
        _state.value = _state.value.copy(
            consoleLines = session.getConsoleLines(instanceId).value,
        )
        loadInstance()
        connectWebSocket()
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
                    error = "加载实例失败: ${e.message}",
                )
            }
        }
    }

    private fun connectWebSocket() {
        val wsClient = session.wsClient
        wsClient.connect(viewModelScope)
        viewModelScope.launch {
            delay(500)
            wsClient.subscribe(instanceId)
        }
        viewModelScope.launch {
            wsClient.messages.collect { msg ->
                if (msg.instanceId != instanceId) return@collect
                when (msg.type) {
                    WsMessage.CONSOLE_OUTPUT -> {
                        try {
                            val payload = json.decodeFromJsonElement<ConsoleOutputPayload>(msg.payload)
                            session.appendConsoleLine(instanceId, payload.line)
                            _state.value = _state.value.copy(
                                consoleLines = session.getConsoleLines(instanceId).value,
                            )
                        } catch (e: Exception) {
                            log.warning("Failed to parse console output: ${e.message}")
                        }
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
                                playerPollJob?.cancel()
                                playerPollJob = viewModelScope.launch {
                                    refreshPlayerNames()
                                    while (isActive) {
                                        delay(30_000)
                                        refreshPlayerNames()
                                    }
                                }
                            } else {
                                playerPollJob?.cancel()
                                playerPollJob = null
                                _state.value = _state.value.copy(playerNames = emptyList())
                            }
                        } catch (e: Exception) {
                            log.warning("Failed to parse state change: ${e.message}")
                        }
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
                        } catch (e: Exception) {
                            log.warning("Failed to parse players changed: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshPlayerNames() {
        try {
            val status = apiClient.getServerStatus(instanceId)
            _state.value = _state.value.copy(playerNames = status.players)
        } catch (e: Exception) {
            log.fine("Failed to refresh player names: ${e.message}")
        }
    }

    fun retryDownload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                val updated = apiClient.retryDownload(instanceId)
                _state.value = _state.value.copy(instance = updated, isActionInProgress = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "重试下载失败: ${e.message}",
                )
            }
        }
    }

    fun startServer() {
        viewModelScope.launch {
            if (_state.value.eulaAccepted != true) {
                _state.value = _state.value.copy(showEulaDialog = true)
                return@launch
            }
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                val status = apiClient.startServer(instanceId)
                _state.value = _state.value.copy(
                    instance = _state.value.instance?.copy(state = status.state),
                    isActionInProgress = false,
                )
            } catch (e: ConduitApiException) {
                if (e.errorResponse?.error?.code == "EULA_NOT_ACCEPTED") {
                    _state.value = _state.value.copy(showEulaDialog = true, isActionInProgress = false)
                } else {
                    _state.value = _state.value.copy(
                        isActionInProgress = false,
                        error = "启动失败: ${e.message}",
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "启动失败: ${e.message}",
                )
            }
        }
    }

    fun stopServer() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                val status = apiClient.stopServer(instanceId)
                _state.value = _state.value.copy(
                    instance = _state.value.instance?.copy(state = status.state),
                    isActionInProgress = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "停止失败: ${e.message}",
                )
            }
        }
    }

    fun killServer() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                apiClient.killServer(instanceId)
                _state.value = _state.value.copy(isActionInProgress = false)
                loadInstance()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "强制停止失败: ${e.message}",
                )
            }
        }
    }

    fun deleteInstance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActionInProgress = true, error = null)
            try {
                apiClient.deleteInstance(instanceId)
                _state.value = _state.value.copy(isActionInProgress = false, isDeleted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isActionInProgress = false,
                    error = "删除失败: ${e.message}",
                )
            }
        }
    }

    fun acceptEula() {
        viewModelScope.launch {
            try {
                apiClient.acceptEula(instanceId)
                _state.value = _state.value.copy(eulaAccepted = true, showEulaDialog = false)
                startServer()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "接受 EULA 失败: ${e.message}",
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
                session.appendConsoleLine(instanceId, "> $command")
                _state.value = _state.value.copy(
                    consoleLines = session.getConsoleLines(instanceId).value,
                    commandInput = "",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "发送命令失败: ${e.message}",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerPollJob?.cancel()
    }
}
