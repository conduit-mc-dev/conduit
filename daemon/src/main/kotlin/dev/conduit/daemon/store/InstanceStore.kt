package dev.conduit.daemon.store

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InstanceStore {

    private data class Instance(
        val id: String,
        var name: String,
        var description: String?,
        var state: InstanceState,
        val mcVersion: String,
        val loader: LoaderInfo?,
        var mcPort: Int,
        val jvmArgs: List<String>?,
        val javaPath: String?,
        val createdAt: Instant,
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
        val instance = Instance(
            id = id,
            name = request.name,
            description = request.description,
            state = InstanceState.STOPPED,
            mcVersion = request.mcVersion,
            loader = null,
            mcPort = port,
            jvmArgs = request.jvmArgs,
            javaPath = request.javaPath,
            createdAt = Clock.System.now(),
        )
        instances[id] = instance
        return instance.toSummary()
    }

    fun list(): List<InstanceSummary> =
        instances.values.map { it.toSummary() }

    fun get(id: String): InstanceSummary {
        val instance = instances[id]
            ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")
        return instance.toSummary()
    }

    fun update(id: String, request: UpdateInstanceRequest): InstanceSummary {
        val instance = instances[id]
            ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")

        request.name?.let { newName ->
            validateName(newName)
            if (instances.values.any { it.id != id && it.name.equals(newName, ignoreCase = true) }) {
                throw ApiException(HttpStatusCode.Conflict, "INSTANCE_NAME_CONFLICT", "Instance name already exists")
            }
            instance.name = newName
        }

        request.description?.let { desc ->
            validateDescription(desc)
            instance.description = desc
        }

        return instance.toSummary()
    }

    fun delete(id: String) {
        val instance = instances[id]
            ?: throw ApiException(HttpStatusCode.NotFound, "INSTANCE_NOT_FOUND", "Instance not found")

        if (instance.state != InstanceState.STOPPED) {
            throw ApiException(HttpStatusCode.Conflict, "INSTANCE_RUNNING", "Instance must be stopped before deletion")
        }

        instances.remove(id)
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
