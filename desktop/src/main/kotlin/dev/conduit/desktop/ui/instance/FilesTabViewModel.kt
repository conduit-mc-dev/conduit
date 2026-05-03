package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.model.FileEntry
import dev.conduit.desktop.session.DaemonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FilesTabUiState(
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class FilesTabViewModel(
    private val instanceId: String,
    private val daemonId: String,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val apiClient
        get() = daemonManager.getSession(daemonId)?.getApi() ?: error("No session")

    private val _state = MutableStateFlow(FilesTabUiState(isLoading = true))
    val state: StateFlow<FilesTabUiState> = _state

    init {
        loadFiles("")
    }

    fun loadFiles(path: String) {
        _state.value = _state.value.copy(isLoading = true, currentPath = path)
        viewModelScope.launch {
            try {
                val listing = apiClient.listFiles(instanceId, path)
                _state.value = _state.value.copy(
                    entries = listing.entries.sortedWith(
                        compareByDescending<FileEntry> { it.type == "directory" }
                            .thenBy { it.name },
                    ),
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

    fun navigateToFolder(name: String) {
        val newPath = if (_state.value.currentPath.isEmpty()) name
        else "${_state.value.currentPath}/$name"
        loadFiles(newPath)
    }

    fun navigateUp() {
        val current = _state.value.currentPath
        if (current.isNotEmpty()) {
            loadFiles(current.substringBeforeLast("/", ""))
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val newPath = _state.value.currentPath
            .split("/")
            .take(index + 1)
            .joinToString("/")
        loadFiles(newPath)
    }

    fun deleteFile(name: String) {
        val path = if (_state.value.currentPath.isEmpty()) name
        else "${_state.value.currentPath}/$name"
        viewModelScope.launch {
            try {
                apiClient.deleteFile(instanceId, path)
                loadFiles(_state.value.currentPath)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Delete failed: ${e.message}")
            }
        }
    }
}
