package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.InstanceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class InstanceListUiState(
    val instances: List<InstanceSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class InstanceListViewModel(private val apiClient: ConduitApiClient) : ViewModel() {

    private val _state = MutableStateFlow(InstanceListUiState())
    val state: StateFlow<InstanceListUiState> = _state

    init {
        refresh()
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
}
