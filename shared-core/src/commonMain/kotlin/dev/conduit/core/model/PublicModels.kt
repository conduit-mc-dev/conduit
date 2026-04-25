package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerJson(
    val conduitVersion: Int = 1,
    val instanceId: String,
    val serverName: String,
    val serverDescription: String? = null,
    val mcVersion: String,
    val loader: ServerJsonLoader? = null,
    val modCount: Int,
    val online: Boolean,
    val playerCount: Int,
    val maxPlayers: Int,
    val pack: ServerJsonPack? = null,
    val minecraft: ServerJsonMinecraft,
)

@Serializable
data class ServerJsonLoader(
    val type: String,
    val version: String,
)

@Serializable
data class ServerJsonPack(
    val versionId: String,
    val downloadUrl: String,
    val sha256: String,
    val fileSize: Long,
)

@Serializable
data class ServerJsonMinecraft(
    val host: String? = null,
    val port: Int,
)
