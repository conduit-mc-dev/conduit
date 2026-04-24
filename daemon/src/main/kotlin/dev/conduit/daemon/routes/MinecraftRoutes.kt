package dev.conduit.daemon.routes

import dev.conduit.core.download.MojangClient
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.minecraftRoutes(mojangClient: MojangClient) {
    route("/api/v1/minecraft") {
        get("/versions") {
            val versions = mojangClient.listReleases()
            call.respond(versions)
        }
    }
}
