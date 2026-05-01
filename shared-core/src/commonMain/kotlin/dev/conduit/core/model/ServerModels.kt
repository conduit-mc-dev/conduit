package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoryInfo(
    val usedMb: Long,
    val maxMb: Long,
)

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val conduitVersion: String,
)

@Serializable
data class ServerStatusResponse(
    val state: InstanceState,
    val playerCount: Int,
    val maxPlayers: Int,
    val players: List<String>,
    val uptime: Long,
    val mcVersion: String,
    val loader: LoaderInfo? = null,
    val memory: MemoryInfo? = null,
    val tps: Double? = null,
)

@Serializable
data class EulaResponse(
    val accepted: Boolean,
    val eulaUrl: String = "https://aka.ms/MinecraftEULA",
)

@Serializable
data class AcceptEulaRequest(
    val accepted: Boolean,
)

@Serializable
data class SendCommandRequest(
    val command: String,
)

@Serializable
data class CommandAcceptedResponse(
    val accepted: Boolean = true,
)
