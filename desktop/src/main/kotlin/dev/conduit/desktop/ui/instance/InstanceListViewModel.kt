package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.*
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.ui.components.DaemonGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

data class InstanceListUiState(
    val daemonGroups: List<DaemonGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val installProgress: Map<String, Double> = emptyMap(),
)

class InstanceListViewModel(
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _daemonInstances = MutableStateFlow<Map<String, List<InstanceSummary>>>(emptyMap())
    private val _installProgress = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _connTrigger = MutableStateFlow(0)

    init {
        refresh()
        observeWebSockets()
        observeConnectionStates()
    }

    val state: StateFlow<InstanceListUiState> = combine(
        combine(_daemonInstances, _connTrigger) { instances, _ -> instances },
        daemonManager.sessions, _isLoading, _error, _installProgress
    ) { instances, sessions, loading, error, progress ->
        val groups = sessions.map { session ->
            DaemonGroup(
                daemonId = session.daemonId,
                daemonName = session.daemonName,
                connectionState = session.connectionState.value,
                instances = instances[session.daemonId] ?: emptyList(),
                installProgress = progress,
            )
        }
        InstanceListUiState(daemonGroups = groups, isLoading = loading, error = error, installProgress = progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstanceListUiState())

    private var connStateJob: Job? = null

    private fun observeConnectionStates() {
        viewModelScope.launch {
            daemonManager.sessions.collect { sessions ->
                connStateJob?.cancel()
                connStateJob = launch {
                    sessions.forEach { session ->
                        launch {
                            session.connectionState.collect {
                                _connTrigger.value = _connTrigger.value + 1
                            }
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val sessions = daemonManager.sessions.value
                if (sessions.isEmpty()) {
                    _isLoading.value = false
                    return@launch
                }
                sessions.forEach { session ->
                    val instances = session.getApi().listInstances()
                    _daemonInstances.value = _daemonInstances.value.toMutableMap().apply { put(session.daemonId, instances) }
                }
                _isLoading.value = false
            } catch (e: Exception) {
                _isLoading.value = false
                _error.value = "Failed to load: ${e.message}"
            }
        }
    }

    private var wsCollectorJob: Job? = null

    private fun observeWebSockets() {
        viewModelScope.launch {
            var firstEmission = true
            daemonManager.sessions.collect { sessions ->
                wsCollectorJob?.cancel()
                if (!firstEmission && sessions.isNotEmpty()) refresh()
                firstEmission = false
                wsCollectorJob = launch {
                    sessions.forEach { session ->
                        launch {
                            session.wsClient.messages.collect { msg ->
                                when (msg.type) {
                                    WsMessage.INSTANCE_CREATED, WsMessage.INSTANCE_DELETED -> refresh()
                                    WsMessage.STATE_CHANGED -> {
                                        try {
                                            val payload = json.decodeFromJsonElement<StateChangedPayload>(msg.payload)
                                            _daemonInstances.value = _daemonInstances.value.toMutableMap().apply {
                                                val current = get(session.daemonId) ?: return@apply
                                                put(session.daemonId, current.map { if (it.id == msg.instanceId) it.copy(state = payload.newState) else it })
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    WsMessage.TASK_PROGRESS -> {
                                        try {
                                            val payload = json.decodeFromJsonElement<TaskProgressPayload>(msg.payload)
                                            val instId = msg.instanceId
                                            if (instId != null) {
                                                _installProgress.value = _installProgress.value.toMutableMap().apply {
                                                    put(instId, payload.progress)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }

                                    WsMessage.TASK_COMPLETED -> {
                                        try {
                                            val instId = msg.instanceId
                                            if (instId != null) {
                                                _installProgress.value = _installProgress.value.toMutableMap().apply {
                                                    remove(instId)
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
