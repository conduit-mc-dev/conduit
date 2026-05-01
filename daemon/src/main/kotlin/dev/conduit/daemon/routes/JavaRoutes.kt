package dev.conduit.daemon.routes

import dev.conduit.core.model.JavaInstallation
import dev.conduit.core.model.SetDefaultJavaRequest
import dev.conduit.core.model.UpdateDaemonConfigRequest
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.JavaDetector
import dev.conduit.daemon.store.DaemonConfigStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.javaRoutes(
    javaDetector: JavaDetector,
    daemonConfigStore: DaemonConfigStore,
) {
    route("/api/v1/java") {
        get("/installations") {
            val installations = javaDetector.detectInstallations()
            call.respond(installations)
        }

        put("/default") {
            val request = call.receive<SetDefaultJavaRequest>()
            val installation = javaDetector.validateJavaPath(request.path)
                ?: throw ApiException(
                    HttpStatusCode.UnprocessableEntity,
                    "JAVA_NOT_FOUND",
                    "Not a valid Java executable: ${request.path}",
                )
            daemonConfigStore.update(UpdateDaemonConfigRequest(defaultJavaPath = request.path))
            call.respond(installation.copy(isDefault = true))
        }
    }
}
