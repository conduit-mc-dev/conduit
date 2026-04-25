package dev.conduit.daemon

import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.routes.*
import dev.conduit.daemon.service.*
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

    val appScope = CoroutineScope(coroutineContext + SupervisorJob())
    val broadcaster = WsBroadcaster(AppJson)
    val eulaService = EulaService(dataDirectory)
    val processManager = ServerProcessManager(instanceStore, dataDirectory, broadcaster, appScope, AppJson)

    val serverJarService = ServerJarService(
        mojangClient = mojangClient,
        instanceStore = instanceStore,
        dataDirectory = dataDirectory,
        scope = appScope,
    )
    val rateLimiter = RateLimiter()

    configurePlugins(tokenStore)

    routing {
        publicRoutes()
        pairRoutes(tokenStore, rateLimiter)
        wsRoutes(broadcaster, tokenStore, AppJson)
        authenticate("bearer") {
            instanceRoutes(instanceStore, serverJarService, dataDirectory, broadcaster, AppJson)
            minecraftRoutes(mojangClient)
            serverRoutes(instanceStore, processManager, eulaService)
        }
    }
}
