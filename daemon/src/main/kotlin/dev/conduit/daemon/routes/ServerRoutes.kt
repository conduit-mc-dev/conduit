package dev.conduit.daemon.routes

import dev.conduit.core.model.AcceptEulaRequest
import dev.conduit.core.model.SendCommandRequest
import dev.conduit.core.model.ServerStatusResponse
import dev.conduit.core.model.EulaResponse
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.EulaService
import dev.conduit.daemon.service.ServerProcessManager
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.serverRoutes(
    instanceStore: InstanceStore,
    processManager: ServerProcessManager,
    eulaService: EulaService,
) {
    route("/api/v1/instances/{id}/server") {
        get("/status") {
            val id = call.requireInstanceId()
            val summary = instanceStore.get(id)
            call.respond(ServerStatusResponse(
                state = summary.state,
                eulaAccepted = eulaService.isAccepted(id),
            ))
        }

        get("/eula") {
            val id = call.requireInstanceId()
            call.respond(EulaResponse(accepted = eulaService.isAccepted(id)))
        }

        put("/eula") {
            val id = call.requireInstanceId()
            instanceStore.get(id) // 验证实例存在
            val request = call.receive<AcceptEulaRequest>()
            if (!request.accepted) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "EULA must be accepted")
            }
            eulaService.accept(id)
            call.respond(EulaResponse(accepted = true))
        }

        post("/start") {
            val id = call.requireInstanceId()
            if (!eulaService.isAccepted(id)) {
                throw ApiException(HttpStatusCode.Conflict, "EULA_NOT_ACCEPTED", "EULA must be accepted before starting the server")
            }
            processManager.start(id)
            call.respond(instanceStore.get(id))
        }

        post("/stop") {
            val id = call.requireInstanceId()
            processManager.stop(id)
            call.respond(instanceStore.get(id))
        }

        post("/kill") {
            val id = call.requireInstanceId()
            processManager.kill(id)
            call.respond(instanceStore.get(id))
        }

        post("/command") {
            val id = call.requireInstanceId()
            val request = call.receive<SendCommandRequest>()
            processManager.sendCommand(id, request.command)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
