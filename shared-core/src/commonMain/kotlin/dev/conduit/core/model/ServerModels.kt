package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerStatusResponse(
    val state: InstanceState,
    val eulaAccepted: Boolean,
)

@Serializable
data class EulaResponse(
    val accepted: Boolean,
)

@Serializable
data class AcceptEulaRequest(
    val accepted: Boolean,
)

@Serializable
data class SendCommandRequest(
    val command: String,
)
