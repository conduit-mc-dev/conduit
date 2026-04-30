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
        const val CONSOLE_INPUT = "console.input"
        const val STATE_CHANGED = "server.state_changed"
        const val SERVER_STATS = "server.stats"
        const val SUBSCRIBE = "subscribe"
        const val UNSUBSCRIBE = "unsubscribe"
        const val PING = "ping"
        const val PONG = "pong"
        const val INSTANCE_CREATED = "instance.created"
        const val INSTANCE_DELETED = "instance.deleted"
        const val PACK_DIRTY = "pack.dirty"
        const val TASK_PROGRESS = "task.progress"
        const val TASK_COMPLETED = "task.completed"

        const val CHANNEL_CONSOLE = "console"
        const val CHANNEL_STATS = "stats"
        val DEFAULT_CHANNELS = listOf(CHANNEL_CONSOLE, CHANNEL_STATS)
    }
}

@Serializable
data class ConsoleOutputPayload(
    val line: String,
    val level: String = "info",
)

@Serializable
data class ConsoleInputPayload(
    val command: String,
)

@Serializable
data class StateChangedPayload(
    val oldState: InstanceState,
    val newState: InstanceState,
)

@Serializable
data class SubscribeRequest(
    val type: String = WsMessage.SUBSCRIBE,
    val instanceId: String,
    val channels: List<String> = WsMessage.DEFAULT_CHANNELS,
)

@Serializable
data class UnsubscribeRequest(
    val type: String = WsMessage.UNSUBSCRIBE,
    val instanceId: String,
    val channels: List<String> = WsMessage.DEFAULT_CHANNELS,
)
