package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.*
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.ui.components.DaemonGroup
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
)

class InstanceListViewModel(
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _daemonInstances = MutableStateFlow<Map<String, List<InstanceSummary>>>(emptyMap())

    init {
        refresh()
        observeWebSockets()
    }

    val state: StateFlow<InstanceListUiState> = combine(
        _daemonInstances, daemonManager.sessions, _isLoading, _error
    ) { instances, sessions, loading, error ->
        val groups = sessions.map { session ->
            DaemonGroup(
                daemonId = session.daemonId,
                daemonName = session.daemonName,
                connectionState = session.connectionState.value,
                instances = instances[session.daemonId] ?: emptyList(),
            )
        }
        InstanceListUiState(daemonGroups = groups, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstanceListUiState())

    fun refresh() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                daemonManager.sessions.value.forEach { session ->
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

    private fun observeWebSockets() {
        viewModelScope.launch {
            daemonManager.sessions.value.forEach { session ->
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
                        }
                    }
                }
            }
        }
    }
}
