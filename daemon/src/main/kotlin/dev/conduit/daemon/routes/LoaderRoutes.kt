package dev.conduit.daemon.routes

import dev.conduit.core.model.InstallLoaderRequest
import dev.conduit.core.model.TaskResponse
import dev.conduit.daemon.service.LoaderService
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TaskStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.loaderRoutes(
    instanceStore: InstanceStore,
    loaderService: LoaderService,
) {
    route("/api/v1/instances/{id}/loader") {
        get {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val loader = loaderService.getCurrentLoader(id)
            if (loader == null) {
                call.respondNullable(null)
            } else {
                call.respond(loader)
            }
        }

        get("/available") {
            val id = call.requireInstanceId()
            val instance = instanceStore.get(id)
            val available = loaderService.getAvailableLoaders(instance.mcVersion)
            call.respond(available)
        }

        post("/install") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val request = call.receive<InstallLoaderRequest>()
            val taskId = loaderService.install(id, request.type, request.version)
            call.respond(
                HttpStatusCode.Accepted,
                TaskResponse(taskId = taskId, type = TaskStore.TYPE_LOADER_INSTALL, message = "Installing ${request.type.name} ${request.version}...")
            )
        }

        delete {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            loaderService.uninstall(id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
