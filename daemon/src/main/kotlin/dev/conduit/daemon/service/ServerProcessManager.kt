package dev.conduit.daemon.service

import dev.conduit.core.model.ConsoleOutputPayload
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.StateChangedPayload
import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class ServerProcessManager(
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val broadcaster: WsBroadcaster,
    private val scope: CoroutineScope,
    private val json: Json = Json { encodeDefaults = true },
) {
    private val log = LoggerFactory.getLogger(ServerProcessManager::class.java)

    private data class ManagedProcess(
        val process: Process,
        val stdin: BufferedWriter,
        val outputJob: Job,
        val monitorJob: Job,
    )

    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    private val donePattern = Regex("""\[.*]: Done \(""")

    fun start(instanceId: String) {
        if (processes.containsKey(instanceId)) {
            throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is already running")
        }

        val config = instanceStore.getProcessConfig(instanceId)
        instanceStore.transitionState(instanceId, InstanceState.STOPPED, InstanceState.STARTING)
        broadcastStateChanged(instanceId, InstanceState.STARTING)

        val instanceDir = dataDirectory.instanceDir(instanceId).toFile()
        val command = buildList {
            add(config.javaPath)
            addAll(config.jvmArgs)
            add("-jar")
            add("server.jar")
            add("nogui")
        }

        val process = ProcessBuilder(command)
            .directory(instanceDir)
            .redirectErrorStream(true)
            .start()

        val stdin = process.outputStream.bufferedWriter()

        val outputJob = scope.launch(Dispatchers.IO) {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        broadcastConsoleLine(instanceId, line)

                        if (donePattern.containsMatchIn(line)) {
                            try {
                                instanceStore.transitionState(instanceId, InstanceState.STARTING, InstanceState.RUNNING)
                                broadcastStateChanged(instanceId, InstanceState.RUNNING)
                            } catch (_: ApiException) {
                                // 状态已不是 STARTING（可能被 stop 抢先），忽略
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    log.warn("Error reading output for instance {}", instanceId, e)
                }
            }
        }

        val monitorJob = scope.launch(Dispatchers.IO) {
            val exitCode = process.waitFor()
            processes.remove(instanceId)
            val existingMessage = try { instanceStore.get(instanceId).statusMessage } catch (_: Exception) { null }
            val statusMessage = when {
                existingMessage?.startsWith("Initialization failed") == true -> existingMessage
                exitCode != 0 -> "Process exited with code $exitCode"
                else -> null
            }
            instanceStore.forceState(instanceId, InstanceState.STOPPED, statusMessage)
            broadcastStateChanged(instanceId, InstanceState.STOPPED, statusMessage)
            log.info("Instance {} process exited with code {}", instanceId, exitCode)
        }

        processes[instanceId] = ManagedProcess(process, stdin, outputJob, monitorJob)
    }

    fun stop(instanceId: String) {
        instanceStore.transitionState(instanceId, InstanceState.RUNNING, InstanceState.STOPPING)
        broadcastStateChanged(instanceId, InstanceState.STOPPING)

        val managed = processes[instanceId]
        if (managed == null) {
            instanceStore.forceState(instanceId, InstanceState.STOPPED)
            broadcastStateChanged(instanceId, InstanceState.STOPPED)
            return
        }

        try {
            managed.stdin.write("stop\n")
            managed.stdin.flush()
        } catch (e: Exception) {
            log.warn("Failed to send stop command to instance {}", instanceId, e)
        }

        // 超时后强制终止
        scope.launch {
            delay(30.seconds)
            if (processes.containsKey(instanceId)) {
                log.warn("Instance {} did not stop within 30s, force killing", instanceId)
                managed.process.destroyForcibly()
            }
        }
    }

    fun kill(instanceId: String) {
        val managed = processes[instanceId]
        if (managed == null) {
            instanceStore.forceState(instanceId, InstanceState.STOPPED)
            return
        }
        managed.process.destroyForcibly()
        // monitorJob 会处理后续状态清理
    }

    fun sendCommand(instanceId: String, command: String) {
        val managed = processes[instanceId]
            ?: throw ApiException(HttpStatusCode.Conflict, "SERVER_NOT_RUNNING", "Server is not running")
        try {
            managed.stdin.write("$command\n")
            managed.stdin.flush()
        } catch (e: Exception) {
            throw ApiException(HttpStatusCode.InternalServerError, "COMMAND_FAILED", "Failed to send command: ${e.message}")
        }
    }

    fun isRunning(instanceId: String): Boolean = processes.containsKey(instanceId)

    fun shutdownAll() {
        for ((instanceId, managed) in processes) {
            log.info("Shutting down instance {}", instanceId)
            try {
                managed.stdin.write("stop\n")
                managed.stdin.flush()
            } catch (_: Exception) {
                // ignore
            }
        }

        scope.launch {
            delay(10.seconds)
            for ((instanceId, managed) in processes) {
                if (managed.process.isAlive) {
                    log.warn("Force killing instance {} on shutdown", instanceId)
                    managed.process.destroyForcibly()
                }
            }
        }
    }

    private fun broadcastConsoleLine(instanceId: String, line: String) {
        val payload = json.encodeToJsonElement(ConsoleOutputPayload(line))
        scope.launch {
            broadcaster.broadcast(instanceId, WsMessage.CONSOLE_OUTPUT, payload)
        }
    }

    private fun broadcastStateChanged(instanceId: String, state: InstanceState, statusMessage: String? = null) {
        val payload = json.encodeToJsonElement(StateChangedPayload(state, statusMessage))
        scope.launch {
            broadcaster.broadcast(instanceId, WsMessage.STATE_CHANGED, payload)
        }
    }
}
