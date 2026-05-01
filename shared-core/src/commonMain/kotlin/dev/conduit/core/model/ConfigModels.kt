package dev.conduit.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 下载源 ---

@Serializable
enum class DownloadSource {
    @SerialName("mojang") MOJANG,
    @SerialName("bmclapi") BMCLAPI,
    @SerialName("custom") CUSTOM,
}

// --- Daemon 全局配置 ---

@Serializable
data class DaemonConfig(
    val port: Int = 9147,
    val publicEndpointEnabled: Boolean = true,
    val defaultJvmArgs: List<String> = listOf("-Xmx4G", "-Xms2G"),
    val downloadSource: DownloadSource = DownloadSource.MOJANG,
    val customMirrorUrl: String? = null,
    val autoRestartEnabled: Boolean = false,
    val autoRestartMaxTimes: Int = 3,
    val crashLoopTimeoutSeconds: Int = 60,
)

@Serializable
data class UpdateDaemonConfigRequest(
    val port: Int? = null,
    val publicEndpointEnabled: Boolean? = null,
    val defaultJvmArgs: List<String>? = null,
    val downloadSource: DownloadSource? = null,
    val customMirrorUrl: String? = null,
    val autoRestartEnabled: Boolean? = null,
    val autoRestartMaxTimes: Int? = null,
    val crashLoopTimeoutSeconds: Int? = null,
)

// --- 实例 JVM 配置 ---

@Serializable
data class JvmConfig(
    val jvmArgs: List<String>?,
    val javaPath: String?,
    val effectiveJavaPath: String,
)

@Serializable
data class UpdateJvmConfigRequest(
    val jvmArgs: List<String>? = null,
    val javaPath: String? = null,
)

// --- 邀请链接 ---

@Serializable
data class InviteInfo(
    val url: String,
    val publicEndpointEnabled: Boolean,
)

@Serializable
data class UpdateInviteRequest(
    val publicEndpointEnabled: Boolean,
)
