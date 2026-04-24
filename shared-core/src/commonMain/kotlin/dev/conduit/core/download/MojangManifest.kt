package dev.conduit.core.download

import kotlinx.serialization.Serializable

const val MOJANG_VERSION_MANIFEST_URL =
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

@Serializable
data class MojangVersionManifest(
    val latest: MojangLatest,
    val versions: List<MojangVersionEntry>,
)

@Serializable
data class MojangLatest(
    val release: String,
    val snapshot: String,
)

@Serializable
data class MojangVersionEntry(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String,
)

@Serializable
data class MojangVersionDetail(
    val id: String,
    val type: String,
    val downloads: MojangDownloads,
)

@Serializable
data class MojangDownloads(
    val server: MojangDownloadEntry? = null,
    val client: MojangDownloadEntry? = null,
)

@Serializable
data class MojangDownloadEntry(
    val sha1: String,
    val size: Long,
    val url: String,
)
