package dev.conduit.daemon

import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.routes.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.ServerJarService
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TokenStore
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

fun main() {
    embeddedServer(Netty, port = 9147, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module(
    tokenStore: TokenStore = TokenStore(),
    instanceStore: InstanceStore = InstanceStore(),
    mojangClient: MojangClient = MojangClient(),
    dataDirectory: DataDirectory = DataDirectory(),
) {
    dataDirectory.ensureDirectories()

    val serverJarService = ServerJarService(
        mojangClient = mojangClient,
        instanceStore = instanceStore,
        dataDirectory = dataDirectory,
        scope = CoroutineScope(coroutineContext + SupervisorJob()),
    )

    configurePlugins(tokenStore)

    routing {
        publicRoutes()
        pairRoutes(tokenStore)
        authenticate("bearer") {
            instanceRoutes(instanceStore, serverJarService)
            minecraftRoutes(mojangClient)
        }
    }
}
