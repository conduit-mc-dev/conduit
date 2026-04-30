package dev.conduit.daemon.service

import dev.conduit.core.mcping.MinecraftPingClient
import dev.conduit.core.model.ConsoleOutputPayload
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.PlayersChangedPayload
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ServerProcessManager(
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val broadcaster: WsBroadcaster,
    private val scope: CoroutineScope,
    private val pingClient: MinecraftPingClient = MinecraftPingClient(),
    private val json: Json = Json { encodeDefaults = true },
) {
    private val log = LoggerFactory.getLogger(ServerProcessManager::class.java)

    private companion object {
        const val STARTUP_TIMEOUT_SECONDS = 60L
    }

    private data class ManagedProcess(
        val process: Process,
        val stdin: BufferedWriter,
        val outputJob: Job,
        val monitorJob: Job,
        val startedAt: Instant,
        @Volatile var pingJob: Job? = null,
        @Volatile var startupTimeoutJob: Job? = null,
    )

    private val processes = ConcurrentHashMap<String, ManagedProcess>()
    private val startupTimedOut = ConcurrentHashMap<String, Boolean>()

    private val logDetector = LogPatternDetector()

    fun start(instanceId: String) {
        if (processes.containsKey(instanceId)) {
            throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is already running")
        }

        val config = instanceStore.getProcessConfig(instanceId)
        val launchTarget = resolveLaunchTarget(instanceStore.getLoader(instanceId))
        instanceStore.transitionState(instanceId, InstanceState.STOPPED, InstanceState.STARTING)
        broadcastStateChanged(instanceId, InstanceState.STOPPED, InstanceState.STARTING)

        val instanceDir = dataDirectory.instanceDir(instanceId).toFile()
        val command = buildList {
            add(config.javaPath)
            addAll(config.jvmArgs)
            when (launchTarget) {
                is LaunchTarget.VanillaJar -> {
                    add("-jar")
                    add("server.jar")
                }
                is LaunchTarget.ArgFile -> add("@${launchTarget.argFilePath}")
            }
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

                        val event = logDetector.detect(line)
                        when (event?.type) {
                            LogEventType.SERVER_DONE -> {
                                try {
                                    instanceStore.transitionState(instanceId, InstanceState.STARTING, InstanceState.RUNNING)
                                    broadcastStateChanged(instanceId, InstanceState.STARTING, InstanceState.RUNNING)
                                    processes[instanceId]?.startupTimeoutJob?.cancel()
                                    startPingPolling(instanceId)
                                } catch (_: ApiException) {
                                    // 状态已不是 STARTING（被 stop 抢先），忽略
                                }
                            }
                            LogEventType.OOM, LogEventType.PORT_CONFLICT, LogEventType.CRASH -> {
                                log.warn("Detected {} for instance {}: {}", event.type, instanceId, line)
                            }
                            null -> {}
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
            val removed = processes.remove(instanceId)
            removed?.pingJob?.cancel()
            removed?.startupTimeoutJob?.cancel()
            val timedOut = startupTimedOut.remove(instanceId) == true
            // 清玩家信息，前端人数归零；若之前非 0 则广播一次
            val hadPlayers = instanceStore.updatePlayerInfo(instanceId, 0, 20, emptyList())
            if (hadPlayers) broadcastPlayersChanged(instanceId, 0, 20)
            val snapshot = try { instanceStore.get(instanceId) } catch (_: Exception) { null }
            val oldState = snapshot?.state ?: InstanceState.RUNNING
            val existingMessage = snapshot?.statusMessage
            val statusMessage = when {
                existingMessage?.startsWith("Initialization failed") == true -> existingMessage
                timedOut -> "Startup timed out after ${STARTUP_TIMEOUT_SECONDS}s"
                exitCode != 0 -> "Process exited with code $exitCode"
                else -> null
            }
            instanceStore.forceState(instanceId, InstanceState.STOPPED, statusMessage)
            broadcastStateChanged(instanceId, oldState, InstanceState.STOPPED)
            log.info("Instance {} process exited with code {}", instanceId, exitCode)
        }

        processes[instanceId] = ManagedProcess(process, stdin, outputJob, monitorJob, Clock.System.now())

        val managed = processes[instanceId]
        managed?.startupTimeoutJob = scope.launch {
            delay(STARTUP_TIMEOUT_SECONDS.seconds)
            val current = try { instanceStore.get(instanceId).state } catch (_: Exception) { return@launch }
            if (current == InstanceState.STARTING) {
                log.warn("Instance {} stuck in STARTING after {}s, forcing stop", instanceId, STARTUP_TIMEOUT_SECONDS)
                startupTimedOut[instanceId] = true
                managed.process.destroyForcibly()
            }
        }
    }

    fun stop(instanceId: String) {
        processes[instanceId]?.startupTimeoutJob?.cancel()
        val current = instanceStore.get(instanceId).state
        when (current) {
            InstanceState.STOPPED -> throw ApiException(HttpStatusCode.Conflict, "SERVER_NOT_RUNNING", "Server is not running")
            InstanceState.INITIALIZING -> throw ApiException(HttpStatusCode.Conflict, "INSTANCE_INITIALIZING", "Instance is still initializing")
            InstanceState.STARTING, InstanceState.STOPPING -> throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is in a transitional state")
            InstanceState.RUNNING -> {} // proceed
        }
        instanceStore.transitionState(instanceId, InstanceState.RUNNING, InstanceState.STOPPING)
        broadcastStateChanged(instanceId, InstanceState.RUNNING, InstanceState.STOPPING)

        val managed = processes[instanceId]
        if (managed == null) {
            instanceStore.forceState(instanceId, InstanceState.STOPPED)
            broadcastStateChanged(instanceId, InstanceState.STOPPING, InstanceState.STOPPED)
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
            val current = instanceStore.get(instanceId).state
            if (current == InstanceState.STOPPED) {
                throw ApiException(HttpStatusCode.Conflict, "SERVER_NOT_RUNNING", "Server is not running")
            }
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

    suspend fun awaitProcessExit(instanceId: String) {
        processes[instanceId]?.monitorJob?.join()
    }

    fun getUptimeSeconds(instanceId: String): Long {
        val managed = processes[instanceId] ?: return 0
        return (Clock.System.now() - managed.startedAt).inWholeSeconds
    }

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

    private fun broadcastStateChanged(instanceId: String, oldState: InstanceState, newState: InstanceState) {
        val payload = json.encodeToJsonElement(StateChangedPayload(oldState, newState))
        scope.launch {
            broadcaster.broadcast(instanceId, WsMessage.STATE_CHANGED, payload)
        }
    }

    private fun broadcastPlayersChanged(instanceId: String, playerCount: Int, maxPlayers: Int) {
        val payload = json.encodeToJsonElement(PlayersChangedPayload(playerCount, maxPlayers))
        scope.launch {
            broadcaster.broadcast(instanceId, WsMessage.PLAYERS_CHANGED, payload)
        }
    }

    private fun startPingPolling(instanceId: String) {
        val mcPort = runCatching { instanceStore.getProcessConfig(instanceId).mcPort }.getOrNull() ?: return
        val managed = processes[instanceId] ?: return
        val job = scope.launch(Dispatchers.IO) {
            delay(5.seconds) // Grace period: MC opens socket slightly after "Done"
            while (isActive) {
                val result = runCatching { pingClient.ping("127.0.0.1", mcPort) }.getOrNull()
                if (result != null) {
                    val changed = instanceStore.updatePlayerInfo(
                        instanceId,
                        playerCount = result.onlinePlayers,
                        maxPlayers = result.maxPlayers,
                        sample = result.sample,
                    )
                    if (changed) broadcastPlayersChanged(instanceId, result.onlinePlayers, result.maxPlayers)
                }
                delay(30.seconds)
            }
        }
        managed.pingJob = job
    }
}
