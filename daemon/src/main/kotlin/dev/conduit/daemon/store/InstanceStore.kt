package dev.conduit.daemon.store

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.AppJson
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import kotlin.io.path.*
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ProcessConfig(
    val jvmArgs: List<String>,
    val javaPath: String,
    val mcPort: Int,
)

class InstanceStore(
    private val dataDirectory: DataDirectory? = null,
) {

    private val log = LoggerFactory.getLogger(InstanceStore::class.java)

    @Serializable
    private data class PersistedInstance(
        val id: String,
        val name: String,
        val description: String? = null,
        val state: InstanceState,
        val mcVersion: String,
        val loader: LoaderInfo? = null,
        val mcPort: Int,
        val jvmArgs: List<String>? = null,
        val javaPath: String? = null,
        val publicEndpointEnabled: Boolean = true,
        val createdAt: Instant,
    )

    private data class Instance(
        val id: String,
        val name: String,
        val description: String?,
        val state: InstanceState,
        val mcVersion: String,
        val loader: LoaderInfo?,
        val mcPort: Int,
        val jvmArgs: List<String>?,
        val javaPath: String?,
        val publicEndpointEnabled: Boolean,
        val createdAt: Instant,
        val taskId: String?,
        val statusMessage: String?,
        val playerCount: Int = 0,
        val maxPlayers: Int = 20,
        val playerSample: List<String> = emptyList(),
    ) {
        fun toSummary() = InstanceSummary(
            id = id,
            name = name,
            description = description,
            state = state,
            mcVersion = mcVersion,
            loader = loader,
            mcPort = mcPort,
            playerCount = playerCount,
            maxPlayers = maxPlayers,
            createdAt = createdAt,
            taskId = taskId,
            statusMessage = statusMessage,
        )

        fun toPersisted() = PersistedInstance(
            id = id, name = name, description = description,
            state = state, mcVersion = mcVersion, loader = loader,
            mcPort = mcPort, jvmArgs = jvmArgs, javaPath = javaPath,
            publicEndpointEnabled = publicEndpointEnabled, createdAt = createdAt,
        )
    }

    private val instances = ConcurrentHashMap<String, Instance>()

    init {
        loadFromDisk()
    }

    fun create(request: CreateInstanceRequest): InstanceSummary {
        validateName(request.name)
        request.description?.let { validateDescription(it) }

        if (instances.values.any { it.name.equals(request.name, ignoreCase = true) }) {
            throw ApiException(HttpStatusCode.Conflict, "INSTANCE_NAME_CONFLICT", "Instance name already exists")
        }

        val port = request.mcPort ?: assignPort()
        if (request.mcPort != null && instances.values.any { it.mcPort == port }) {
            throw ApiException(HttpStatusCode.Conflict, "PORT_CONFLICT", "Port $port is already in use")
        }

        val id = generateUniqueId()
        val taskId = IdGenerator.generateTaskId()
        val instance = Instance(
            id = id,
            name = request.name,
            description = request.description,
            state = InstanceState.INITIALIZING,
            mcVersion = request.mcVersion,
            loader = null,
            mcPort = port,
            jvmArgs = request.jvmArgs,
            javaPath = request.javaPath,
            publicEndpointEnabled = true,
            createdAt = Clock.System.now(),
            taskId = taskId,
            statusMessage = "Downloading server.jar...",
        )
        instances[id] = instance
        dataDirectory?.instanceDir(id)?.createDirectories()
        persist(instance)
        return instance.toSummary()
    }

    fun markInitialized(id: String) {
        val updated = instances.compute(id) { _, existing ->
            existing?.copy(state = InstanceState.STOPPED, taskId = null, statusMessage = null)
        }
        updated?.let { persist(it) }
    }

    fun markInitializationFailed(id: String, reason: String) {
        val updated = instances.compute(id) { _, existing ->
            existing?.copy(
                state = InstanceState.STOPPED,
                taskId = null,
                statusMessage = "Initialization failed: $reason",
            )
        }
        updated?.let { persist(it) }
    }

    fun list(): List<InstanceSummary> =
        instances.values.map { it.toSummary() }

    fun get(id: String): InstanceSummary {
        val instance = instances[id]
            ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
        return instance.toSummary()
    }

    fun update(id: String, request: UpdateInstanceRequest): InstanceSummary {
        var result: InstanceSummary? = null
        val updated = instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            var inst: Instance = existing
            request.name?.let { newName ->
                validateName(newName)
                if (instances.values.any { it.id != id && it.name.equals(newName, ignoreCase = true) }) {
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_NAME_CONFLICT", "Instance name already exists")
                }
                inst = inst.copy(name = newName)
            }
            request.description?.let { desc ->
                validateDescription(desc)
                inst = inst.copy(description = desc)
            }
            result = inst.toSummary()
            inst
        }
        updated?.let { persist(it) }
        return result ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
    }

    fun resetToInitializing(id: String): InstanceSummary {
        var result: InstanceSummary? = null
        val updated = instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            if (existing.state != InstanceState.STOPPED) {
                throw ApiException(HttpStatusCode.Conflict, "INSTANCE_RUNNING", "Instance must be stopped to retry download")
            }
            val inst = existing.copy(
                state = InstanceState.INITIALIZING,
                taskId = IdGenerator.generateTaskId(),
                statusMessage = "Downloading server.jar...",
            )
            result = inst.toSummary()
            inst
        }
        updated?.let { persist(it) }
        return result!!
    }

    fun delete(id: String) {
        var deleted = false
        instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            when (existing.state) {
                InstanceState.STOPPED -> { deleted = true; null }
                InstanceState.INITIALIZING ->
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_INITIALIZING", "Instance is still initializing")
                else ->
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_RUNNING", "Instance must be stopped before deletion")
            }
        }
        if (deleted) deletePersistedMetadata(id)
    }

    fun transitionState(id: String, from: InstanceState, to: InstanceState, statusMessage: String? = null): InstanceSummary {
        var result: InstanceSummary? = null
        val updated = instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            if (existing.state != from) {
                throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "Instance is ${existing.state}, expected $from")
            }
            val inst = existing.copy(state = to, statusMessage = statusMessage)
            result = inst.toSummary()
            inst
        }
        updated?.let { persist(it) }
        return result!!
    }

    fun forceState(id: String, to: InstanceState, statusMessage: String? = null) {
        val updated = instances.compute(id) { _, existing ->
            existing?.copy(state = to, statusMessage = statusMessage)
        }
        updated?.let { persist(it) }
    }

    fun getProcessConfig(id: String, defaultJavaPath: String? = null): ProcessConfig {
        val instance = requireInstance(id)
        return ProcessConfig(
            jvmArgs = instance.jvmArgs ?: emptyList(),
            javaPath = instance.javaPath ?: defaultJavaPath ?: "java",
            mcPort = instance.mcPort,
        )
    }

    fun getJvmArgs(id: String): List<String>? = requireInstance(id).jvmArgs

    fun getJavaPath(id: String): String? = requireInstance(id).javaPath

    data class JvmConfigData(val jvmArgs: List<String>?, val javaPath: String?)

    fun getJvmConfigData(id: String): JvmConfigData {
        val instance = requireInstance(id)
        return JvmConfigData(jvmArgs = instance.jvmArgs, javaPath = instance.javaPath)
    }

    fun updateJvmConfig(id: String, updateJvmArgs: Boolean, jvmArgs: List<String>?, updateJavaPath: Boolean, javaPath: String?) {
        computeAndPersist(id) { instance ->
            instance.copy(
                jvmArgs = if (updateJvmArgs) jvmArgs else instance.jvmArgs,
                javaPath = if (updateJavaPath) javaPath else instance.javaPath,
            )
        }
    }

    fun isPublicEndpointEnabled(id: String): Boolean = requireInstance(id).publicEndpointEnabled

    fun setPublicEndpointEnabled(id: String, enabled: Boolean) {
        computeAndPersist(id) { it.copy(publicEndpointEnabled = enabled) }
    }

    fun setLoader(id: String, loader: LoaderInfo?) {
        computeAndPersist(id) { it.copy(loader = loader) }
    }

    fun getLoader(id: String): LoaderInfo? = requireInstance(id).loader

    fun getPlayerSample(id: String): List<String> = requireInstance(id).playerSample

    /**
     * Updates the live player info from an MC Server List Ping response.
     * Returns true iff `playerCount` or `maxPlayers` actually changed (useful for deciding whether to broadcast).
     */
    fun updatePlayerInfo(id: String, playerCount: Int, maxPlayers: Int, sample: List<String>): Boolean {
        var changed = false
        instances.compute(id) { _, existing ->
            if (existing == null) return@compute null
            if (existing.playerCount != playerCount || existing.maxPlayers != maxPlayers) changed = true
            existing.copy(playerCount = playerCount, maxPlayers = maxPlayers, playerSample = sample)
        }
        return changed
    }

    private fun requireInstance(id: String): Instance =
        instances[id] ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")

    private fun computeAndPersist(id: String, transform: (Instance) -> Instance) {
        val updated = instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            transform(existing)
        }
        updated?.let { persist(it) }
    }

    private fun persist(instance: Instance) {
        val dir = dataDirectory ?: return
        try {
            val path = dir.instanceMetadataPath(instance.id)
            path.writeText(AppJson.encodeToString(instance.toPersisted()))
        } catch (e: Exception) {
            log.warn("Failed to persist instance {}", instance.id, e)
        }
    }

    private fun deletePersistedMetadata(id: String) {
        val dir = dataDirectory ?: return
        try {
            dir.instanceMetadataPath(id).deleteIfExists()
        } catch (e: Exception) {
            log.warn("Failed to delete instance.json for {}", id, e)
        }
    }

    private fun loadFromDisk() {
        val dir = dataDirectory ?: return
        if (!dir.instancesDir.exists()) return

        dir.instancesDir.listDirectoryEntries().forEach { instanceDir ->
            if (!instanceDir.isDirectory()) return@forEach
            val metadataPath = instanceDir.resolve("instance.json")
            if (!metadataPath.exists()) return@forEach

            try {
                val persisted = AppJson.decodeFromString<PersistedInstance>(metadataPath.readText())
                val instance = Instance(
                    id = persisted.id, name = persisted.name, description = persisted.description,
                    state = InstanceState.STOPPED, mcVersion = persisted.mcVersion, loader = persisted.loader,
                    mcPort = persisted.mcPort, jvmArgs = persisted.jvmArgs, javaPath = persisted.javaPath,
                    publicEndpointEnabled = persisted.publicEndpointEnabled, createdAt = persisted.createdAt,
                    taskId = null, statusMessage = statusMessageForRecovery(persisted.state),
                )
                instances[instance.id] = instance
                if (persisted.state != InstanceState.STOPPED) {
                    persist(instance)
                }
                log.info("Loaded instance {} ({}), state: {} -> STOPPED", instance.id, instance.name, persisted.state)
            } catch (e: Exception) {
                log.warn("Failed to load instance from {}, skipping", metadataPath, e)
            }
        }
    }

    private fun statusMessageForRecovery(persisted: InstanceState): String? = when (persisted) {
        InstanceState.RUNNING, InstanceState.STARTING, InstanceState.STOPPING ->
            "Recovered after daemon restart"
        InstanceState.INITIALIZING ->
            "Initialization interrupted -- retry download"
        InstanceState.STOPPED -> null
    }

    private fun generateUniqueId(): String {
        repeat(100) {
            val id = IdGenerator.generateInstanceId()
            if (!instances.containsKey(id)) return id
        }
        throw ApiException(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "Failed to generate unique instance ID")
    }

    private fun assignPort(): Int {
        val usedPorts = instances.values.map { it.mcPort }.toSet()
        var port = 25565
        while (port in usedPorts) port++
        return port
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name.length > 64) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Name must be 1-64 characters")
        }
    }

    private fun validateDescription(description: String) {
        if (description.length > 256) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Description must not exceed 256 characters")
        }
    }
}
