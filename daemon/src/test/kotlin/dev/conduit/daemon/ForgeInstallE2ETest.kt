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
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Real-network Forge install smoke test. Skipped unless CONDUIT_RUN_SLOW_TESTS=true.
 *
 * Hits maven.minecraftforge.net and launcher.mojang.com for real. Downloads ~45 MB
 * of vanilla server.jar plus ~150 MB of Forge libraries. Expect 2-4 minutes total.
 *
 * Bypasses ktor's testApplication (runTest has a 60s timeout that this test exceeds).
 * Boots embeddedServer on a random port and talks to it via CIO.
 *
 * Prerequisites:
 * - `java` on PATH is Java 17+ (Forge 1.20.4 requirement)
 * - Outbound HTTPS to maven.minecraftforge.net, maven.neoforged.net, launcher.mojang.com
 *
 * To run:
 *   CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests "*ForgeInstallE2ETest*" --rerun-tasks
 */
class ForgeInstallE2ETest {

    private val forgeVersion = "1.20.4-49.0.14"
    private val mcVersion = "1.20.4"

    @Test
    fun `Forge install downloads installer, runs --installServer, produces argfile`() = runBlocking {
        assumeTrue(
            "Set CONDUIT_RUN_SLOW_TESTS=true to opt in to this slow network test",
            System.getenv("CONDUIT_RUN_SLOW_TESTS") == "true",
        )

        val tempDir = Files.createTempDirectory("conduit-forge-e2e")
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
            defaultRequest {
                url("http://127.0.0.1:$port")
            }
        }

        try {
            val token = pairViaReal(client)

            val instance = client.post("/api/v1/instances") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(CreateInstanceRequest(name = "Forge E2E", mcVersion = mcVersion))
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
                    setBody(InstallLoaderRequest(type = LoaderType.FORGE, version = forgeVersion))
                }.body<TaskResponse>()

                val completion = awaitTaskCompletion(installResp.taskId, timeoutMs = 300_000)
                assertTrue(
                    completion.success,
                    "Forge install task failed: ${completion.message}",
                )
            }

            val argFile = tempDir
                .resolve("instances/${instance.id}")
                .resolve("libraries/net/minecraftforge/forge/$forgeVersion/unix_args.txt")
            assertTrue(argFile.exists(), "Expected argfile at $argFile after Forge install")

            val loader = client.get("/api/v1/instances/${instance.id}/loader") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<LoaderInfo>()
            assertEquals(LoaderType.FORGE, loader.type)
            assertEquals(forgeVersion, loader.version)
        } finally {
            client.close()
            server.stop(1_000, 5_000)
            tempDir.toFile().deleteRecursively()
        }
    }

    private suspend fun pairViaReal(client: HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "e2e-forge"))
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
