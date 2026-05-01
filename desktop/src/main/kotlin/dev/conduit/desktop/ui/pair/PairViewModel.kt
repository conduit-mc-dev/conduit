package dev.conduit.desktop.ui.pair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PairUiState(
    val daemonUrl: String = "http://localhost:9147",
    val pairCode: String = "",
    val deviceName: String = "",
    val step: PairStep = PairStep.CONNECT,
    val isLoading: Boolean = false,
    val error: String? = null,
    val healthStatus: String? = null,
)

enum class PairStep { CONNECT, ENTER_CODE, DONE }

class PairViewModel(private val apiClient: ConduitApiClient) : ViewModel() {

    private val _state = MutableStateFlow(PairUiState())
    val state: StateFlow<PairUiState> = _state

    fun updateDaemonUrl(url: String) {
        _state.value = _state.value.copy(daemonUrl = url, error = null)
    }

    fun updatePairCode(code: String) {
        _state.value = _state.value.copy(pairCode = code, error = null)
    }

    fun updateDeviceName(name: String) {
        _state.value = _state.value.copy(deviceName = name, error = null)
    }

    fun connect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                apiClient.setBaseUrl(_state.value.daemonUrl.trimEnd('/'))
                val health = apiClient.health()
                _state.value = _state.value.copy(
                    isLoading = false,
                    healthStatus = "Conduit Daemon v${health.conduitVersion}",
                    step = PairStep.ENTER_CODE,
                )
            } catch (e: ConduitApiException) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "连接失败: ${e.message}",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.message ?: "无法连接到 Daemon"}",
                )
            }
        }
    }

    fun confirmPairing(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.pairCode.isBlank() || s.deviceName.isBlank()) {
            _state.value = s.copy(error = "配对码和设备名不能为空")
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val response = apiClient.confirmPairing(s.pairCode.trim(), s.deviceName.trim())
                apiClient.setToken(response.token)
                _state.value = _state.value.copy(isLoading = false, step = PairStep.DONE)
                onSuccess()
            } catch (e: ConduitApiException) {
                val msg = when (e.httpStatus) {
                    400, 404 -> "配对码无效或已过期"
                    else -> "配对失败: ${e.message}"
                }
                _state.value = _state.value.copy(isLoading = false, error = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.message ?: "请检查网络连接"}",
                )
            }
        }
    }
}
