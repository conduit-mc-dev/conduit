package dev.conduit.daemon.service

import dev.conduit.core.download.MojangClient
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.StateChangedPayload
import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TaskStore
import dev.conduit.daemon.store.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class ServerJarService(
    private val mojangClient: MojangClient,
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val taskStore: TaskStore,
    private val broadcaster: WsBroadcaster,
    private val json: Json,
    private val scope: CoroutineScope,
) {

    private val log = LoggerFactory.getLogger(ServerJarService::class.java)

    private val downloadJobs = ConcurrentHashMap<String, Job>()

    fun startDownload(instanceId: String, mcVersion: String, taskId: String) {
        val job = scope.launch {
            taskStore.create(instanceId, TaskStore.TYPE_SERVER_JAR_DOWNLOAD, "Downloading server.jar...", taskId = taskId)
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
                broadcaster.broadcast(instanceId, WsMessage.STATE_CHANGED,
                    json.encodeToJsonElement(StateChangedPayload(
                        InstanceState.INITIALIZING, InstanceState.STOPPED
                    )))
                taskStore.complete(taskId, success = true, "Server JAR downloaded successfully")
            } catch (e: CancellationException) {
                dataDirectory.serverJarPath(instanceId).deleteIfExists()
                taskStore.cancel(taskId, "Download cancelled")
                throw e
            } catch (e: Exception) {
                log.error("Failed to download server.jar for instance {}", instanceId, e)
                instanceStore.markInitializationFailed(instanceId, e.message ?: "Unknown error")
                taskStore.complete(taskId, success = false, "Download failed: ${e.message}")
            }
        }
        downloadJobs[taskId] = job
    }

    suspend fun cancelDownload(taskId: String) {
        val task = taskStore.get(taskId) ?: return
        if (task.status != TaskStatus.RUNNING) return
        downloadJobs.remove(taskId)?.cancel()
        dataDirectory.serverJarPath(task.instanceId).deleteIfExists()
        taskStore.cancel(taskId, "Download cancelled by user")
    }
}
