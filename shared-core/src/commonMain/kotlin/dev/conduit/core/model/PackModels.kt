package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class PackInfo(
    val name: String,
    val versionId: String,
    val lastBuiltAt: String? = null,
    val dirty: Boolean,
    val fileSize: Long = 0,
    val modCount: Int = 0,
    val hash: PackHash? = null,
)

@Serializable
data class PackHash(
    val sha256: String,
)

@Serializable
data class BuildPackRequest(
    val versionId: String? = null,
    val summary: String? = null,
)

@Serializable
data class BuildStatusResponse(
    val state: String,
    val progress: Double = 0.0,
    val message: String = "",
)
