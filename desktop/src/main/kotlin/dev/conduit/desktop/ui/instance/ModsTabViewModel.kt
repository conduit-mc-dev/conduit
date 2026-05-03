package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.InstalledMod
import dev.conduit.core.model.ToggleModRequest
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ModFilter { ALL, ENABLED, DISABLED }

data class ModsTabUiState(
    val mods: List<InstalledMod> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val filter: ModFilter = ModFilter.ALL,
) {
    val filteredMods: List<InstalledMod>
        get() {
            var result = mods
            when (filter) {
                ModFilter.ENABLED -> result = result.filter { it.enabled }
                ModFilter.DISABLED -> result = result.filter { !it.enabled }
                ModFilter.ALL -> {}
            }
            if (searchQuery.isNotBlank()) {
                result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
            }
            return result
        }
}

class ModsTabViewModel(
    private val instanceId: String,
    private val daemonId: String,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val apiClient
        get() = daemonManager.getSession(daemonId)?.getApi() ?: error("No session")

    private val _state = MutableStateFlow(ModsTabUiState(isLoading = true))
    val state: StateFlow<ModsTabUiState> = _state

    init {
        loadMods()
    }

    fun loadMods() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    mods = apiClient.listMods(instanceId),
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

    fun toggleMod(modId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                apiClient.toggleMod(instanceId, modId, ToggleModRequest(enabled))
                _state.value = _state.value.copy(
                    mods = _state.value.mods.map {
                        if (it.id == modId) it.copy(enabled = enabled) else it
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Toggle failed: ${e.message}")
            }
        }
    }

    fun removeMod(modId: String) {
        viewModelScope.launch {
            try {
                apiClient.removeMod(instanceId, modId)
                _state.value = _state.value.copy(
                    mods = _state.value.mods.filter { it.id != modId },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Remove failed: ${e.message}")
            }
        }
    }

    fun updateSearch(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setFilter(filter: ModFilter) {
        _state.value = _state.value.copy(filter = filter)
    }
}
