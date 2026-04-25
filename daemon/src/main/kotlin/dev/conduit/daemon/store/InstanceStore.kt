package dev.conduit.daemon.store

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class ProcessConfig(
    val jvmArgs: List<String>,
    val javaPath: String,
    val mcPort: Int,
)

class InstanceStore {

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
    ) {
        fun toSummary() = InstanceSummary(
            id = id,
            name = name,
            description = description,
            state = state,
            mcVersion = mcVersion,
            loader = loader,
            mcPort = mcPort,
            playerCount = 0,
            maxPlayers = 20,
            createdAt = createdAt,
            taskId = taskId,
            statusMessage = statusMessage,
        )
    }

    private val instances = ConcurrentHashMap<String, Instance>()

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
        return instance.toSummary()
    }

    fun markInitialized(id: String) {
        instances.compute(id) { _, existing ->
            existing?.copy(state = InstanceState.STOPPED, taskId = null, statusMessage = null)
        }
    }

    fun markInitializationFailed(id: String, reason: String) {
        instances.compute(id) { _, existing ->
            existing?.copy(
                state = InstanceState.STOPPED,
                taskId = null,
                statusMessage = "Initialization failed: $reason",
            )
        }
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
        instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            var updated: Instance = existing
            request.name?.let { newName ->
                validateName(newName)
                if (instances.values.any { it.id != id && it.name.equals(newName, ignoreCase = true) }) {
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_NAME_CONFLICT", "Instance name already exists")
                }
                updated = updated.copy(name = newName)
            }
            request.description?.let { desc ->
                validateDescription(desc)
                updated = updated.copy(description = desc)
            }
            result = updated.toSummary()
            updated
        }
        return result ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
    }

    fun resetToInitializing(id: String): InstanceSummary {
        var result: InstanceSummary? = null
        instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            if (existing.state != InstanceState.STOPPED) {
                throw ApiException(HttpStatusCode.Conflict, "INSTANCE_RUNNING", "Instance must be stopped to retry download")
            }
            val updated = existing.copy(
                state = InstanceState.INITIALIZING,
                taskId = IdGenerator.generateTaskId(),
                statusMessage = "Downloading server.jar...",
            )
            result = updated.toSummary()
            updated
        }
        return result!!
    }

    fun delete(id: String) {
        val instance = instances[id]
            ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")

        when (instance.state) {
            InstanceState.STOPPED -> {} // allow
            InstanceState.INITIALIZING ->
                throw ApiException(HttpStatusCode.Conflict, "INSTANCE_INITIALIZING", "Instance is still initializing")
            else ->
                throw ApiException(HttpStatusCode.Conflict, "INSTANCE_RUNNING", "Instance must be stopped before deletion")
        }

        instances.remove(id)
    }

    fun transitionState(id: String, from: InstanceState, to: InstanceState, statusMessage: String? = null): InstanceSummary {
        var result: InstanceSummary? = null
        instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            if (existing.state != from) {
                throw ApiException(HttpStatusCode.Conflict, "INVALID_STATE", "Instance is ${existing.state}, expected $from")
            }
            val updated = existing.copy(state = to, statusMessage = statusMessage)
            result = updated.toSummary()
            updated
        }
        return result!!
    }

    fun forceState(id: String, to: InstanceState, statusMessage: String? = null) {
        instances.compute(id) { _, existing ->
            existing?.copy(state = to, statusMessage = statusMessage)
        }
    }

    fun getProcessConfig(id: String): ProcessConfig {
        val instance = requireInstance(id)
        return ProcessConfig(
            jvmArgs = instance.jvmArgs ?: emptyList(),
            javaPath = instance.javaPath ?: "java",
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
        computeInstance(id) { instance ->
            instance.copy(
                jvmArgs = if (updateJvmArgs) jvmArgs else instance.jvmArgs,
                javaPath = if (updateJavaPath) javaPath else instance.javaPath,
            )
        }
    }

    fun isPublicEndpointEnabled(id: String): Boolean = requireInstance(id).publicEndpointEnabled

    fun setPublicEndpointEnabled(id: String, enabled: Boolean) {
        computeInstance(id) { it.copy(publicEndpointEnabled = enabled) }
    }

    fun setLoader(id: String, loader: LoaderInfo?) {
        computeInstance(id) { it.copy(loader = loader) }
    }

    fun getLoader(id: String): LoaderInfo? = requireInstance(id).loader

    private fun requireInstance(id: String): Instance =
        instances[id] ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")

    private fun computeInstance(id: String, transform: (Instance) -> Instance) {
        instances.compute(id) { _, existing ->
            if (existing == null) throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
            transform(existing)
        }
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
