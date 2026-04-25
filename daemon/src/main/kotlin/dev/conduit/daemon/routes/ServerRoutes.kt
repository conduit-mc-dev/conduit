package dev.conduit.daemon.routes

import dev.conduit.core.model.*
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
            call.respond(buildServerStatus(id, instanceStore, processManager))
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
            val summary = instanceStore.get(id)

            when (summary.state) {
                InstanceState.INITIALIZING ->
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_INITIALIZING", "Instance is still initializing")
                InstanceState.RUNNING -> {
                    // 幂等：已运行则返回当前状态
                    call.respond(buildServerStatus(id, instanceStore, processManager))
                    return@post
                }
                InstanceState.STARTING, InstanceState.STOPPING ->
                    throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is in a transitional state")
                InstanceState.STOPPED -> {} // proceed
            }

            if (!eulaService.isAccepted(id)) {
                throw ApiException(HttpStatusCode.Conflict, "EULA_NOT_ACCEPTED", "EULA must be accepted before starting the server")
            }
            processManager.start(id)
            call.respond(buildServerStatus(id, instanceStore, processManager))
        }

        post("/stop") {
            val id = call.requireInstanceId()
            processManager.stop(id)
            call.respond(buildServerStatus(id, instanceStore, processManager))
        }

        post("/restart") {
            val id = call.requireInstanceId()
            val summary = instanceStore.get(id)

            if (!eulaService.isAccepted(id)) {
                throw ApiException(HttpStatusCode.Conflict, "EULA_NOT_ACCEPTED", "EULA must be accepted before starting the server")
            }

            when (summary.state) {
                InstanceState.INITIALIZING ->
                    throw ApiException(HttpStatusCode.Conflict, "INSTANCE_INITIALIZING", "Instance is still initializing")
                InstanceState.STARTING, InstanceState.STOPPING ->
                    throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is in a transitional state")
                InstanceState.RUNNING -> {
                    processManager.stop(id)
                    processManager.awaitProcessExit(id)
                    processManager.start(id)
                }
                InstanceState.STOPPED -> processManager.start(id)
            }
            call.respond(buildServerStatus(id, instanceStore, processManager))
        }

        post("/kill") {
            val id = call.requireInstanceId()
            processManager.kill(id)
            call.respond(buildServerStatus(id, instanceStore, processManager))
        }

        post("/command") {
            val id = call.requireInstanceId()
            val request = call.receive<SendCommandRequest>()
            processManager.sendCommand(id, request.command)
            call.respond(HttpStatusCode.OK, CommandAcceptedResponse())
        }
    }
}

private fun buildServerStatus(
    id: String,
    instanceStore: InstanceStore,
    processManager: ServerProcessManager,
): ServerStatusResponse {
    val summary = instanceStore.get(id)
    return ServerStatusResponse(
        state = summary.state,
        playerCount = summary.playerCount,
        maxPlayers = summary.maxPlayers,
        players = emptyList(),
        uptime = processManager.getUptimeSeconds(id),
        mcVersion = summary.mcVersion,
        loader = summary.loader,
        memory = null,
        tps = null,
    )
}
