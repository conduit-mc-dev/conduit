package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthSearchHit>,
    val totalHits: Int,
    val offset: Int,
    val limit: Int,
)

@Serializable
data class ModrinthSearchHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val iconUrl: String? = null,
    val downloads: Long = 0,
    val latestVersion: String? = null,
    val categories: List<String> = emptyList(),
    val env: ModEnvSupport? = null,
)

@Serializable
data class ModEnvSupport(
    val client: String? = null,
    val server: String? = null,
)

@Serializable
data class ModrinthVersionInfo(
    val versionId: String,
    val projectId: String? = null,
    val versionNumber: String,
    val name: String,
    val changelog: String? = null,
    val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val datePublished: String,
    val files: List<ModrinthVersionFile> = emptyList(),
    val dependencies: List<ModrinthDependency> = emptyList(),
    val clientSide: String? = null,
    val serverSide: String? = null,
)

@Serializable
data class ModrinthVersionFile(
    val fileName: String,
    val fileSize: Long,
    val url: String? = null,
    val hashes: ModrinthFileHashes? = null,
)

@Serializable
data class ModrinthFileHashes(
    val sha1: String? = null,
    val sha512: String? = null,
)

@Serializable
data class ModrinthDependency(
    val projectId: String? = null,
    val projectName: String? = null,
    val dependencyType: String,
)
