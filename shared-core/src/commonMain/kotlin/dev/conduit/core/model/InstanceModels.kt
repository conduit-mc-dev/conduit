package dev.conduit.core.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class LoaderInfo(
    val type: LoaderType,
    val version: String,
    val mcVersion: String? = null,
)

@Serializable
data class InstanceSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val state: InstanceState,
    val mcVersion: String,
    val loader: LoaderInfo? = null,
    val mcPort: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val createdAt: Instant,
    val taskId: String? = null,
    val statusMessage: String? = null,
)

@Serializable
data class CreateInstanceRequest(
    val name: String,
    val mcVersion: String,
    val description: String? = null,
    val mcPort: Int? = null,
    val jvmArgs: List<String>? = null,
    val javaPath: String? = null,
)

@Serializable
data class UpdateInstanceRequest(
    val name: String? = null,
    val description: String? = null,
)
