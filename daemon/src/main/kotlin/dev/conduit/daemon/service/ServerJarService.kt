package dev.conduit.daemon.service

import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.store.InstanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ServerJarService(
    private val mojangClient: MojangClient,
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val scope: CoroutineScope,
) {

    private val log = LoggerFactory.getLogger(ServerJarService::class.java)

    fun startDownload(instanceId: String, mcVersion: String) {
        scope.launch {
            try {
                val destination = dataDirectory.serverJarPath(instanceId)
                log.info("Downloading server.jar for instance {} (MC {})", instanceId, mcVersion)

                val bytes = mojangClient.downloadServerJar(mcVersion, destination)
                log.info("Downloaded server.jar for instance {} ({} bytes)", instanceId, bytes)

                instanceStore.markInitialized(instanceId)
            } catch (e: Exception) {
                log.error("Failed to download server.jar for instance {}", instanceId, e)
                instanceStore.markInitializationFailed(instanceId, e.message ?: "Unknown error")
            }
        }
    }
}
