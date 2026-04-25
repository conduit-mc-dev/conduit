package dev.conduit.daemon.routes

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.UpdateInstanceRequest
import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.ServerJarService
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.io.path.deleteIfExists

fun Route.instanceRoutes(
    instanceStore: InstanceStore,
    serverJarService: ServerJarService,
    dataDirectory: DataDirectory,
    broadcaster: WsBroadcaster,
    json: Json,
) {
    route("/api/v1/instances") {
        get {
            call.respond(instanceStore.list())
        }

        post {
            val request = call.receive<CreateInstanceRequest>()
            if (request.mcVersion.isBlank()) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "mcVersion must not be empty")
            }
            val summary = instanceStore.create(request)
            serverJarService.startDownload(summary.id, request.mcVersion)
            broadcaster.broadcastGlobal(
                WsMessage.INSTANCE_CREATED,
                json.encodeToJsonElement(mapOf("id" to summary.id, "name" to summary.name)),
            )
            call.respond(HttpStatusCode.Created, summary)
        }

        get("/{id}") {
            val id = call.requireInstanceId()
            call.respond(instanceStore.get(id))
        }

        put("/{id}") {
            val id = call.requireInstanceId()
            val request = call.receive<UpdateInstanceRequest>()
            call.respond(instanceStore.update(id, request))
        }

        delete("/{id}") {
            val id = call.requireInstanceId()
            instanceStore.delete(id)
            broadcaster.broadcastGlobal(
                WsMessage.INSTANCE_DELETED,
                json.encodeToJsonElement(mapOf("id" to id)),
            )
            call.respond(HttpStatusCode.NoContent)
        }

        post("/{id}/retry-download") {
            val id = call.requireInstanceId()
            val summary = instanceStore.resetToInitializing(id)
            dataDirectory.serverJarPath(id).deleteIfExists()
            serverJarService.startDownload(id, summary.mcVersion)
            call.respond(summary)
        }
    }
}
