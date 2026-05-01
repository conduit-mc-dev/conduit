package dev.conduit.daemon

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.download.MojangClient
import dev.conduit.daemon.routes.*
import dev.conduit.daemon.service.*
import dev.conduit.daemon.store.DaemonConfigStore
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TokenStore
import io.ktor.client.*
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
    dataDirectory: DataDirectory = DataDirectory(),
    instanceStore: InstanceStore = InstanceStore(dataDirectory),
    mojangClient: MojangClient? = null,
    modrinthClient: ModrinthClient? = null,
    loaderHttpClient: HttpClient? = null,
) {
    dataDirectory.ensureDirectories()

    val appScope = CoroutineScope(coroutineContext + SupervisorJob())
    val broadcaster = WsBroadcaster(AppJson)
    val eulaService = EulaService(dataDirectory)
    val daemonConfigStore = DaemonConfigStore(dataDirectory.configPath)
    val actualMojangClient = mojangClient ?: MojangClient(
        downloadSourceProvider = { daemonConfigStore.get().let { it.downloadSource to it.customMirrorUrl } },
    )
    val processManager = ServerProcessManager(
        instanceStore, dataDirectory, broadcaster, appScope,
        daemonConfigStore = daemonConfigStore,
        json = AppJson,
    )

    val taskStore = dev.conduit.daemon.store.TaskStore(broadcaster, AppJson)
    val serverJarService = ServerJarService(
        mojangClient = actualMojangClient,
        instanceStore = instanceStore,
        dataDirectory = dataDirectory,
        taskStore = taskStore,
        scope = appScope,
    )
    val rateLimiter = RateLimiter()
    val fileService = FileService(dataDirectory)
    val serverPropertiesService = ServerPropertiesService(dataDirectory)
    val actualModrinthClient = modrinthClient ?: ModrinthClient()
    val javaDetector = JavaDetector()
    val modStore = dev.conduit.daemon.store.ModStore(dataDirectory)
    val modService = ModService(modStore, actualModrinthClient, instanceStore, dataDirectory, broadcaster, AppJson)
    val packStore = dev.conduit.daemon.store.PackStore()
    val loaderService = LoaderService(instanceStore, dataDirectory, taskStore, appScope, loaderHttpClient)
    val packService = PackService(modStore, instanceStore, packStore, dataDirectory, taskStore, appScope, AppJson)

    configurePlugins(tokenStore)

    routing {
        publicRoutes(instanceStore, modStore, packStore, processManager, dataDirectory)
        pairRoutes(tokenStore, rateLimiter)
        wsRoutes(broadcaster, tokenStore, processManager, AppJson)
        authenticate("bearer") {
            instanceRoutes(instanceStore, serverJarService, dataDirectory, broadcaster, AppJson)
            minecraftRoutes(actualMojangClient)
            serverRoutes(instanceStore, processManager, eulaService)
            configRoutes(daemonConfigStore, instanceStore)
            fileRoutes(instanceStore, fileService, serverPropertiesService)
            modrinthRoutes(actualModrinthClient)
            javaRoutes(javaDetector, daemonConfigStore)
            modRoutes(instanceStore, modService)
            loaderRoutes(instanceStore, loaderService)
            packRoutes(instanceStore, packService)
            taskRoutes(taskStore, loaderService, packService, serverJarService)
        }
    }
}
