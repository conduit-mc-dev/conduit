package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledMod(
    val id: String,
    val source: String,
    val modrinthProjectId: String? = null,
    val modrinthVersionId: String? = null,
    val name: String,
    val version: String,
    val fileName: String,
    val env: ModEnvSupport = ModEnvSupport(client = "required", server = "required"),
    val hashes: ModHashes = ModHashes(),
    val downloadUrl: String? = null,
    val fileSize: Long = 0,
    val enabled: Boolean = true,
)

@Serializable
data class ModHashes(
    val sha1: String? = null,
    val sha512: String? = null,
)

@Serializable
data class InstallModRequest(
    val modrinthVersionId: String,
)

@Serializable
data class UpdateModRequest(
    val modrinthVersionId: String,
)

@Serializable
data class ToggleModRequest(
    val enabled: Boolean,
)

@Serializable
data class ModUpdatesResponse(
    val updatesAvailable: Int,
    val mods: List<ModUpdateEntry>,
)

@Serializable
data class ModUpdateEntry(
    val modId: String,
    val name: String,
    val currentVersion: String,
    val latestVersion: String,
    val latestVersionId: String,
    val changelog: String? = null,
)
