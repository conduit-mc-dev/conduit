package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ConfigProperty(val key: String, val originalValue: String, val currentValue: String) {
    val isModified: Boolean get() = currentValue != originalValue
}

data class ConfigTabUiState(
    val properties: List<ConfigProperty> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
) {
    val modifiedCount: Int get() = properties.count { it.isModified }
    val filteredProperties: List<ConfigProperty>
        get() = if (searchQuery.isBlank()) properties
        else properties.filter {
            it.key.contains(searchQuery, ignoreCase = true) ||
                it.currentValue.contains(searchQuery, ignoreCase = true)
        }
}

class ConfigTabViewModel(
    private val instanceId: String,
    private val daemonId: String,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val apiClient
        get() = daemonManager.getSession(daemonId)?.getApi() ?: error("No session")

    private val _state = MutableStateFlow(ConfigTabUiState())
    val state: StateFlow<ConfigTabUiState> = _state

    init {
        loadProperties()
    }

    fun loadProperties() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val props = apiClient.getServerProperties(instanceId)
                _state.value = _state.value.copy(
                    properties = props.map { ConfigProperty(it.key, it.value, it.value) }
                        .sortedBy { it.key },
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Load failed: ${e.message}",
                )
            }
        }
    }

    fun updateProperty(key: String, newValue: String) {
        _state.value = _state.value.copy(
            properties = _state.value.properties.map {
                if (it.key == key) it.copy(currentValue = newValue) else it
            },
        )
    }

    fun revertAll() {
        _state.value = _state.value.copy(
            properties = _state.value.properties.map {
                it.copy(currentValue = it.originalValue)
            },
        )
    }

    fun save() {
        val modified = _state.value.properties.filter { it.isModified }
        if (modified.isEmpty()) return
        _state.value = _state.value.copy(isSaving = true)
        viewModelScope.launch {
            try {
                apiClient.updateServerProperties(
                    instanceId,
                    modified.associate { it.key to it.currentValue },
                )
                loadProperties()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = "Save failed: ${e.message}",
                )
            }
        }
    }

    fun updateSearch(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }
}
