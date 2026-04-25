package dev.conduit.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WsMessage(
    val type: String,
    val instanceId: String? = null,
    val payload: JsonElement,
    val timestamp: Instant,
) {
    companion object {
        const val CONSOLE_OUTPUT = "console.output"
        const val STATE_CHANGED = "state_changed"
        const val SUBSCRIBE = "subscribe"
        const val UNSUBSCRIBE = "unsubscribe"
    }
}

@Serializable
data class ConsoleOutputPayload(
    val line: String,
)

@Serializable
data class StateChangedPayload(
    val state: InstanceState,
    val statusMessage: String? = null,
)

@Serializable
data class SubscribeRequest(
    val type: String = WsMessage.SUBSCRIBE,
    val instanceId: String,
)

@Serializable
data class UnsubscribeRequest(
    val type: String = WsMessage.UNSUBSCRIBE,
    val instanceId: String,
)
