package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CreateInstanceState(val name: String = "", val mcVersion: String = "", val port: Int = 25565, val isCreating: Boolean = false, val error: String? = null)

class CreateInstanceViewModel(private val daemonId: String, private val daemonManager: DaemonManager) : ViewModel() {
    private val apiClient get() = daemonManager.getSession(daemonId)?.getApi() ?: error("No session")
    private val _state = MutableStateFlow(CreateInstanceState())
    val state: StateFlow<CreateInstanceState> = _state

    fun updateName(name: String) { _state.value = _state.value.copy(name = name) }
    fun updateVersion(version: String) { _state.value = _state.value.copy(mcVersion = version) }
    fun updatePort(port: Int) { _state.value = _state.value.copy(port = port) }

    fun create(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) { _state.value = s.copy(error = "Name is required"); return }
        _state.value = s.copy(isCreating = true, error = null)
        viewModelScope.launch {
            try {
                apiClient.createInstance(CreateInstanceRequest(name = s.name, mcVersion = s.mcVersion.ifBlank { "1.21.4" }, mcPort = s.port))
                onSuccess()
            } catch (e: Exception) { _state.value = _state.value.copy(isCreating = false, error = "Create failed: ${e.message}") }
        }
    }
}
