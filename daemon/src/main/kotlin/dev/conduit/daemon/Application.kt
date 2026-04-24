package dev.conduit.daemon

import dev.conduit.daemon.routes.*
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TokenStore
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 9147, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val tokenStore = TokenStore()
    val instanceStore = InstanceStore()

    configurePlugins(tokenStore)

    routing {
        publicRoutes()
        pairRoutes(tokenStore)
        authenticate("bearer") {
            instanceRoutes(instanceStore)
        }
    }
}
