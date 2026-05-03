package dev.conduit.desktop.ui.daemon

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DaemonEditState(val name: String = "", val address: String = "", val port: String = "8080", val isSaving: Boolean = false, val error: String? = null)

class DaemonViewModel(private val daemonManager: DaemonManager) : ViewModel() {
    private val _editState = MutableStateFlow(DaemonEditState())
    val editState: StateFlow<DaemonEditState> = _editState

    fun loadDaemon(daemonId: String) {
        val session = daemonManager.getSession(daemonId) ?: return
        val url = session.daemonUrl
        _editState.value = DaemonEditState(
            name = session.daemonName,
            address = url.removePrefix("http://").removePrefix("https://").substringBeforeLast(":"),
            port = url.substringAfterLast(":", "8080"),
        )
    }

    fun updateName(n: String) { _editState.value = _editState.value.copy(name = n) }
    fun updateAddress(a: String) { _editState.value = _editState.value.copy(address = a) }
    fun updatePort(p: String) { _editState.value = _editState.value.copy(port = p) }

    fun save(daemonId: String, onDone: () -> Unit) {
        val s = _editState.value
        _editState.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                daemonManager.removeDaemon(daemonId)
                daemonManager.addDaemon(daemonId, s.name, "http://${s.address}:${s.port}", "existing-token")
                onDone()
            } catch (e: Exception) { _editState.value = _editState.value.copy(isSaving = false, error = "Save failed: ${e.message}") }
        }
    }

    fun disconnect(daemonId: String) { daemonManager.removeDaemon(daemonId) }
    fun forget(daemonId: String) { daemonManager.removeDaemon(daemonId); daemonManager.clearSession() }
    fun retry(daemonId: String) { daemonManager.getSession(daemonId)?.let { it.stop() /* WS auto-reconnect handles retry */ } }
}
