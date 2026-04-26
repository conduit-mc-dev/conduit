package dev.conduit.daemon.service

import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TaskStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ServerJarService(
    private val mojangClient: MojangClient,
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val taskStore: TaskStore,
    private val scope: CoroutineScope,
) {

    private val log = LoggerFactory.getLogger(ServerJarService::class.java)

    fun startDownload(instanceId: String, mcVersion: String, taskId: String) {
        scope.launch {
            taskStore.register(taskId, instanceId, "server_jar_download", "Downloading server.jar...")
            try {
                val destination = dataDirectory.serverJarPath(instanceId)
                log.info("Downloading server.jar for instance {} (MC {})", instanceId, mcVersion)

                var lastProgress = 0.0
                val bytes = mojangClient.downloadServerJar(mcVersion, destination) { bytesWritten, totalBytes ->
                    if (totalBytes > 0) {
                        val progress = (bytesWritten.toDouble() / totalBytes).coerceIn(0.0, 0.99)
                        if (progress - lastProgress >= 0.05) {
                            lastProgress = progress
                            val mb = bytesWritten / 1_048_576
                            val totalMb = totalBytes / 1_048_576
                            scope.launch {
                                taskStore.updateProgress(taskId, progress, "Downloading... ${mb}MB / ${totalMb}MB")
                            }
                        }
                    }
                }

                log.info("Downloaded server.jar for instance {} ({} bytes)", instanceId, bytes)
                instanceStore.markInitialized(instanceId)
                taskStore.complete(taskId, success = true, "Server JAR downloaded successfully")
            } catch (e: Exception) {
                log.error("Failed to download server.jar for instance {}", instanceId, e)
                instanceStore.markInitializationFailed(instanceId, e.message ?: "Unknown error")
                taskStore.complete(taskId, success = false, "Download failed: ${e.message}")
            }
        }
    }
}
