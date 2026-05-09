package dev.conduit.desktop.ui.components

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ToastType { Success, Error, Warning }
data class ToastMessage(val type: ToastType, val text: String, val id: Long)

class ToastManager(
    private val autoDismissMs: Long = 3000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _toasts = MutableStateFlow<List<ToastMessage>>(emptyList())
    val toasts: StateFlow<List<ToastMessage>> = _toasts.asStateFlow()

    private val nextId = AtomicLong(0)

    fun show(type: ToastType, text: String): Long {
        val id = nextId.incrementAndGet()
        val message = ToastMessage(type, text, id)
        _toasts.value = _toasts.value + message
        scope.launch {
            delay(autoDismissMs)
            dismiss(id)
        }
        return id
    }

    fun dismiss(id: Long) {
        _toasts.value = _toasts.value.filter { it.id != id }
    }
}
