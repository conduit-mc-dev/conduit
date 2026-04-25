package dev.conduit.daemon.routes

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.UpdateInstanceRequest
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.ServerJarService
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.path.deleteIfExists

fun Route.instanceRoutes(instanceStore: InstanceStore, serverJarService: ServerJarService, dataDirectory: DataDirectory) {
    route("/api/v1/instances") {
        get {
            call.respond(instanceStore.list())
        }

        post {
            val request = call.receive<CreateInstanceRequest>()
            val summary = instanceStore.create(request)
            serverJarService.startDownload(summary.id, request.mcVersion)
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
