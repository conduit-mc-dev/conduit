package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.*
import dev.conduit.desktop.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

data class InstanceListUiState(
    val instances: List<InstanceSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class InstanceListViewModel(
    private val apiClient: ConduitApiClient,
    private val session: SessionManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(InstanceListUiState())
    val state: StateFlow<InstanceListUiState> = _state

    init {
        refresh()
        connectWebSocket()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val instances = apiClient.listInstances()
                _state.value = _state.value.copy(instances = instances, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "加载实例列表失败: ${e.message}",
                )
            }
        }
    }

    private fun connectWebSocket() {
        val wsClient = session.wsClient
        wsClient.connect(viewModelScope)
        viewModelScope.launch {
            wsClient.messages.collect { msg ->
                when (msg.type) {
                    WsMessage.INSTANCE_CREATED, WsMessage.INSTANCE_DELETED -> {
                        refresh()
                    }
                    WsMessage.STATE_CHANGED -> {
                        try {
                            val payload = json.decodeFromJsonElement<StateChangedPayload>(msg.payload)
                            _state.value = _state.value.copy(
                                instances = _state.value.instances.map { inst ->
                                    if (inst.id == msg.instanceId) {
                                        inst.copy(state = payload.newState)
                                    } else {
                                        inst
                                    }
                                }
                            )
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
