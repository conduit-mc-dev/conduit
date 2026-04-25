package dev.conduit.daemon.routes

import dev.conduit.core.model.BuildPackRequest
import dev.conduit.core.model.TaskResponse
import dev.conduit.daemon.service.PackService
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.packRoutes(
    instanceStore: InstanceStore,
    packService: PackService,
) {
    route("/api/v1/instances/{id}/pack") {
        get {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            call.respond(packService.getPackInfo(id))
        }

        post("/build") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val request = try {
                call.receive<BuildPackRequest>()
            } catch (_: Exception) {
                BuildPackRequest()
            }
            val taskId = packService.build(id, request.versionId, request.summary)
            call.respond(
                HttpStatusCode.Accepted,
                TaskResponse(taskId = taskId, type = "pack_build", message = "Building pack...")
            )
        }

        get("/build/status") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            call.respond(packService.getBuildStatus(id))
        }
    }
}
