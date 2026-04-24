package dev.conduit.daemon

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 9147, host = "0.0.0.0") {
        routing {
            get("/public/health") {
                call.respondText(
                    """{"status":"ok","conduitVersion":"0.1.0"}""",
                    io.ktor.http.ContentType.Application.Json
                )
            }
        }
    }.start(wait = true)
}
