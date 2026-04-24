package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersion(
    val id: String,
    val type: String,
    val releaseTime: String,
)
