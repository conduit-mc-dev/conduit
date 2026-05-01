package dev.conduit.daemon

import dev.conduit.core.download.MojangClient
import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Real-network mod-loader install smoke tests. Skipped unless CONDUIT_RUN_SLOW_TESTS=true.
 *
 * Hits real loader meta/maven hosts plus launcher.mojang.com. Forge/NeoForge each download
 * ~45 MB of vanilla server.jar plus ~100-200 MB of loader libraries (expect ~100s per case);
 * Fabric/Quilt download ~45 MB vanilla + a single ~30 MB fat server jar, no subprocess,
 * typically under 40s per case.
 *
 * Bypasses ktor's testApplication (runTest has a 60s timeout that a real install
 * exceeds). Boots embeddedServer on a random port and talks to it via CIO, which also
 * exercises the real network stack instead of the test-host in-memory engine.
 *
 * Prerequisites:
 * - `java` on PATH is Java 17+ (Forge/NeoForge 1.20.4 requirement)
 * - Outbound HTTPS to maven.minecraftforge.net, maven.neoforged.net, meta.fabricmc.net,
 *   meta.quiltmc.org, launcher.mojang.com
 *
 * To run:
 *   CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests "*ModLoaderInstallE2ETest*" --rerun-tasks
 */
class ModLoaderInstallE2ETest {

    private val mcVersion = "1.20.4"

    @Test
    fun `NeoForge install produces version-scoped unix argfile`() = runBlocking {
        assumeSlowTests()
        verifyLoaderInstall(
            loader = LoaderType.NEOFORGE,
            version = "20.4.237",
            expectedProduct = ExpectedProduct.ArgFile("libraries/net/neoforged/neoforge/20.4.237/${argFileName()}"),
        )
    }

    @Test
    fun `Forge install produces version-scoped unix argfile`() = runBlocking {
        assumeSlowTests()
        verifyLoaderInstall(
            loader = LoaderType.FORGE,
            version = "1.20.4-49.0.14",
            expectedProduct = ExpectedProduct.ArgFile("libraries/net/minecraftforge/forge/1.20.4-49.0.14/${argFileName()}"),
        )
    }

    @Test
    fun `Fabric install produces server jar`() = runBlocking {
        assumeSlowTests()
        verifyLoaderInstall(
            loader = LoaderType.FABRIC,
            // Hardcoded version of a known-good stable Fabric loader for MC 1.20.4.
            // If meta.fabricmc.net retires it, bump the string.
            version = "0.15.11",
            // Fabric's /server/jar actually returns a ~180KB launcher (not a fat jar). 100KB is a
            // conservative floor that still rejects an empty/error response but allows the real size.
            expectedProduct = ExpectedProduct.ServerJar(minBytes = 100_000),
        )
    }

    @Test
    fun `Quilt install produces launch jar`() = runBlocking {
        assumeSlowTests()
        verifyLoaderInstall(
            loader = LoaderType.QUILT,
            // Latest Quilt loader version at time of writing (meta.quiltmc.org has stopped
            // producing stable releases past the 0.20.0-beta.* series). Bump if Quilt resumes
            // stable releases.
            version = "0.20.0-beta.9",
            expectedProduct = ExpectedProduct.LoaderJar(fileName = "quilt-server-launch.jar", minBytes = 100),
        )
    }

    private sealed class ExpectedProduct {
        data class ArgFile(val relativePath: String) : ExpectedProduct()
        data class ServerJar(val minBytes: Long) : ExpectedProduct()
        data class LoaderJar(val fileName: String, val minBytes: Long) : ExpectedProduct()
    }

    private fun argFileName(): String =
        if (System.getProperty("os.name", "").lowercase().contains("windows")) "win_args.txt" else "unix_args.txt"

    private fun assumeSlowTests() {
        assumeTrue(
            "Set CONDUIT_RUN_SLOW_TESTS=true to opt in to this slow network test",
            System.getenv("CONDUIT_RUN_SLOW_TESTS") == "true",
        )
    }

    private suspend fun verifyLoaderInstall(
        loader: LoaderType,
        version: String,
        expectedProduct: ExpectedProduct,
    ) {
        val tempDir = Files.createTempDirectory("conduit-loader-e2e")
        val port = freePort()
        val dataDir = DataDirectory(tempDir)
        val store = InstanceStore(dataDir)

        val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            module(dataDirectory = dataDir, instanceStore = store, mojangClient = MojangClient())
        }.start(wait = false)

        val client = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(WebSockets)
            install(HttpTimeout) { requestTimeoutMillis = 300_000 }
            defaultRequest { url("http://127.0.0.1:$port") }
        }

        try {
            val token = pairViaReal(client)

            val instance = client.post("/api/v1/instances") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(CreateInstanceRequest(name = "${loader.name} E2E", mcVersion = mcVersion))
            }.body<InstanceSummary>()

            pollUntilInitialized(client, token, instance.id, timeoutMs = 180_000)

            client.put("/api/v1/instances/${instance.id}/server/eula") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(AcceptEulaRequest(accepted = true))
            }

            client.webSocket("ws://127.0.0.1:$port/api/v1/ws?token=$token") {
                val installResp = client.post("/api/v1/instances/${instance.id}/loader/install") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(InstallLoaderRequest(type = loader, version = version))
                }.body<TaskResponse>()

                val completion = awaitTaskCompletion(installResp.taskId, timeoutMs = 300_000)
                assertTrue(
                    completion.success,
                    "${loader.name} install task failed: ${completion.message}",
                )
            }

            val instanceRoot = tempDir.resolve("instances/${instance.id}")
            assertProductPresent(instanceRoot, expectedProduct, loader)

            val loaderInfo = client.get("/api/v1/instances/${instance.id}/loader") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<LoaderInfo>()
            assertEquals(loader, loaderInfo.type)
            assertEquals(version, loaderInfo.version)
        } finally {
            client.close()
            server.stop(1_000, 5_000)
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun assertProductPresent(instanceRoot: Path, expected: ExpectedProduct, loader: LoaderType) {
        when (expected) {
            is ExpectedProduct.ArgFile -> {
                val argFile = instanceRoot.resolve(expected.relativePath)
                assertTrue(argFile.exists(), "Expected argfile at $argFile after ${loader.name} install")
            }
            is ExpectedProduct.ServerJar -> {
                val jar = instanceRoot.resolve("server.jar")
                assertTrue(jar.exists(), "Expected server.jar at $jar after ${loader.name} install")
                val size = jar.fileSize()
                assertTrue(
                    size >= expected.minBytes,
                    "${loader.name} server.jar is suspiciously small: $size bytes (expected >= ${expected.minBytes})",
                )
            }
            is ExpectedProduct.LoaderJar -> {
                val jar = instanceRoot.resolve(expected.fileName)
                assertTrue(jar.exists(), "Expected ${expected.fileName} at $jar after ${loader.name} install")
                val size = jar.fileSize()
                assertTrue(
                    size >= expected.minBytes,
                    "${loader.name} ${expected.fileName} is suspiciously small: $size bytes (expected >= ${expected.minBytes})",
                )
                // LoaderJar mode also requires vanilla server.jar to remain intact (the loader reads it as game jar).
                val vanilla = instanceRoot.resolve("server.jar")
                assertTrue(vanilla.exists(), "vanilla server.jar must survive ${loader.name} install at $vanilla")
            }
        }
    }

    private suspend fun pairViaReal(client: HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "e2e-loader"))
        }.body<PairConfirmResponse>().token
    }

    private suspend fun pollUntilInitialized(
        client: HttpClient,
        token: String,
        instanceId: String,
        timeoutMs: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val resp = client.get("/api/v1/instances/$instanceId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<InstanceSummary>()
            if (resp.state != InstanceState.INITIALIZING) return
            delay(500)
        }
        fail("Timed out waiting for instance $instanceId to finish initializing")
    }

    private data class TaskCompletion(val success: Boolean, val message: String?)

    private suspend fun DefaultClientWebSocketSession.awaitTaskCompletion(
        taskId: String,
        timeoutMs: Long,
    ): TaskCompletion {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val frame = incoming.tryReceive().getOrNull() as? Frame.Text ?: run {
                delay(250)
                continue
            }
            val msg = Json.parseToJsonElement(frame.readText()).jsonObject
            if (msg["type"]?.jsonPrimitive?.content != WsMessage.TASK_COMPLETED) continue
            val payload = msg["payload"]?.jsonObject ?: continue
            if (payload["taskId"]?.jsonPrimitive?.content != taskId) continue
            val success = payload["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val message = payload["message"]?.jsonPrimitive?.content
            return TaskCompletion(success, message)
        }
        fail("Timed out waiting for task $taskId to complete")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
