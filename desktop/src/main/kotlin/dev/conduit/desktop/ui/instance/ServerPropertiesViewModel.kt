package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ServerPropertiesUiState(
    val properties: Map<String, String> = emptyMap(),
    val editedValues: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    val restartRequired: Boolean = false,
)

class ServerPropertiesViewModel(
    private val instanceId: String,
    private val apiClient: ConduitApiClient,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerPropertiesUiState())
    val state: StateFlow<ServerPropertiesUiState> = _state

    init {
        loadProperties()
    }

    fun loadProperties() {
        TODO("Task 3")
    }

    fun updateValue(key: String, value: String) {
        TODO("Task 3")
    }

    fun save() {
        TODO("Task 3")
    }

    fun dismissSuccess() {
        _state.value = _state.value.copy(saveSuccess = false)
    }
}
