package dev.conduit.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WsMessage(
    val type: String,
    val instanceId: String? = null,
    val payload: JsonElement,
    val timestamp: Instant,
)
