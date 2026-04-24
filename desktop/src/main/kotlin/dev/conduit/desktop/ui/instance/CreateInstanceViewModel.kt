package dev.conduit.desktop.ui.instance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.MinecraftVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CreateInstanceUiState(
    val name: String = "",
    val description: String = "",
    val mcPort: String = "",
    val versions: List<MinecraftVersion> = emptyList(),
    val selectedVersion: MinecraftVersion? = null,
    val isLoadingVersions: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val versionsError: String? = null,
)

class CreateInstanceViewModel(private val apiClient: ConduitApiClient) : ViewModel() {

    private val _state = MutableStateFlow(CreateInstanceUiState())
    val state: StateFlow<CreateInstanceUiState> = _state

    init {
        loadVersions()
    }

    fun updateName(name: String) {
        _state.value = _state.value.copy(name = name, error = null)
    }

    fun updateDescription(description: String) {
        _state.value = _state.value.copy(description = description)
    }

    fun updateMcPort(port: String) {
        _state.value = _state.value.copy(mcPort = port, error = null)
    }

    fun selectVersion(version: MinecraftVersion) {
        _state.value = _state.value.copy(selectedVersion = version, error = null)
    }

    fun loadVersions() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingVersions = true, versionsError = null)
            try {
                val versions = apiClient.listMinecraftVersions()
                _state.value = _state.value.copy(
                    versions = versions,
                    selectedVersion = versions.firstOrNull(),
                    isLoadingVersions = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingVersions = false,
                    versionsError = "加载版本列表失败: ${e.message}",
                )
            }
        }
    }

    fun createInstance(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.value = current.copy(error = "请输入实例名称")
            return
        }
        if (current.selectedVersion == null) {
            _state.value = current.copy(error = "请选择 MC 版本")
            return
        }

        val port = if (current.mcPort.isBlank()) null else {
            current.mcPort.toIntOrNull()?.also {
                if (it !in 1024..65535) {
                    _state.value = current.copy(error = "端口范围 1024-65535")
                    return
                }
            } ?: run {
                _state.value = current.copy(error = "端口必须为数字")
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true, error = null)
            try {
                apiClient.createInstance(
                    CreateInstanceRequest(
                        name = current.name.trim(),
                        mcVersion = current.selectedVersion.id,
                        description = current.description.trim().ifEmpty { null },
                        mcPort = port,
                    ),
                )
                onSuccess()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCreating = false,
                    error = "创建失败: ${e.message}",
                )
            }
        }
    }
}
