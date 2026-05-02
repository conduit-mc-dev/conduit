package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.ServerPropertiesUpdateResponse
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
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val properties = apiClient.getServerProperties(instanceId)
                _state.value = _state.value.copy(
                    properties = properties,
                    editedValues = emptyMap(),
                    isLoading = false,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "加载服务器配置失败: ${e.message}",
                )
            }
        }
    }

    fun updateValue(key: String, value: String) {
        val current = _state.value.editedValues.toMutableMap()
        val original = _state.value.properties[key]
        if (value == original) {
            current.remove(key)
        } else {
            current[key] = value
        }
        _state.value = _state.value.copy(editedValues = current)
    }

    fun save() {
        val changes = _state.value.editedValues
        if (changes.isEmpty()) return

        _state.value = _state.value.copy(isSaving = true, error = null, saveSuccess = false)
        viewModelScope.launch {
            try {
                val response = apiClient.updateServerProperties(instanceId, changes)
                _state.value = _state.value.copy(
                    properties = _state.value.properties + changes,
                    editedValues = emptyMap(),
                    isSaving = false,
                    saveSuccess = true,
                    restartRequired = response.restartRequired,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "保存配置失败: ${e.message}",
                )
            }
        }
    }

    fun dismissSuccess() {
        _state.value = _state.value.copy(saveSuccess = false)
    }
}
