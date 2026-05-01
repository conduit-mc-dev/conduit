package dev.conduit.daemon.store

import dev.conduit.core.model.DaemonConfig
import dev.conduit.core.model.UpdateDaemonConfigRequest
import dev.conduit.daemon.AppJson
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DaemonConfigStore(private val configPath: Path) {

    @Volatile
    private var config: DaemonConfig = load()

    fun get(): DaemonConfig = config

    fun update(request: UpdateDaemonConfigRequest): DaemonConfig {
        val updated = config.copy(
            port = request.port ?: config.port,
            publicEndpointEnabled = request.publicEndpointEnabled ?: config.publicEndpointEnabled,
            defaultJvmArgs = request.defaultJvmArgs ?: config.defaultJvmArgs,
            downloadSource = request.downloadSource ?: config.downloadSource,
            customMirrorUrl = request.customMirrorUrl ?: config.customMirrorUrl,
            autoRestartEnabled = request.autoRestartEnabled ?: config.autoRestartEnabled,
            autoRestartMaxTimes = request.autoRestartMaxTimes ?: config.autoRestartMaxTimes,
            crashLoopTimeoutSeconds = request.crashLoopTimeoutSeconds ?: config.crashLoopTimeoutSeconds,
            defaultJavaPath = request.defaultJavaPath ?: config.defaultJavaPath,
        )
        config = updated
        save(updated)
        return updated
    }

    private fun load(): DaemonConfig {
        if (!configPath.exists()) return DaemonConfig()
        return try {
            AppJson.decodeFromString<DaemonConfig>(configPath.readText())
        } catch (_: Exception) {
            DaemonConfig()
        }
    }

    private fun save(config: DaemonConfig) {
        configPath.writeText(AppJson.encodeToString(config))
    }
}
