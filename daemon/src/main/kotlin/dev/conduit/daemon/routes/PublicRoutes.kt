package dev.conduit.daemon.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.publicRoutes() {
    route("/public") {
        get("/health") {
            call.respond(mapOf("status" to "ok", "conduitVersion" to "0.1.0"))
        }
    }
}
