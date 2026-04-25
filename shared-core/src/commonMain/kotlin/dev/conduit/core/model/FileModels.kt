package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DirectoryListing(
    val path: String,
    val entries: List<FileEntry>,
)

@Serializable
data class FileEntry(
    val name: String,
    val type: String,
    val size: Long?,
    val lastModified: String,
)

@Serializable
data class FileWriteResponse(
    val path: String,
    val size: Long,
    val lastModified: String,
)

@Serializable
data class ServerPropertiesUpdateResponse(
    val updated: List<String>,
    val restartRequired: Boolean,
)
