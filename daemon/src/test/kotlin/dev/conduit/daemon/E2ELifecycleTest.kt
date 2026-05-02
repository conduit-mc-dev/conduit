package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class E2ELifecycleTest {

    private fun mockServerJvmConfig(): Pair<String, List<String>> {
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        return Pair(javaPath, listOf("-cp", classpath, MockMcServer::class.qualifiedName!!))
    }

    private suspend fun pollUntilState(
        client: HttpClient, token: String, instanceId: String,
        targetState: String, timeoutMs: Long = 10_000,
    ): ServerStatusResponse {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val resp = client.get("/api/v1/instances/$instanceId/server/status") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<ServerStatusResponse>()
            if (resp.state.name.lowercase() == targetState) return resp
            delay(200)
        }
        fail("Timeout waiting for state $targetState on instance $instanceId")
    }

    private suspend fun waitUntilReady(
        client: HttpClient, token: String, instanceId: String, timeoutMs: Long = 10_000,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val resp = client.get("/api/v1/instances/$instanceId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<InstanceSummary>()
            if (resp.state != InstanceState.INITIALIZING) return
            delay(200)
        }
        fail("Timeout waiting for instance $instanceId to finish initializing")
    }

    private data class MockServerContext(
        val client: HttpClient,
        val token: String,
        val instanceId: String,
        val store: InstanceStore,
    )

    private fun runWithMockServer(
        testName: String = "E2E Test",
        skipStart: Boolean = false,
        block: suspend ApplicationTestBuilder.(MockServerContext) -> Unit,
    ) {
        val tempDir = Files.createTempDirectory("conduit-e2e")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = mockServerJvmConfig()

            testApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val instance = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = testName, mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                waitUntilReady(client, token, instance.id)

                client.put("/api/v1/instances/${instance.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }

                store.updateJvmConfig(instance.id, true, jvmArgs, true, javaPath)

                if (!skipStart) {
                    client.post("/api/v1/instances/${instance.id}/server/start") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    pollUntilState(client, token, instance.id, "running")
                }

                try {
                    block(MockServerContext(client, token, instance.id, store))
                } finally {
                    if (!skipStart) {
                        runCatching {
                            client.post("/api/v1/instances/${instance.id}/server/stop") {
                                header(HttpHeaders.Authorization, "Bearer $token")
                            }
                            pollUntilState(client, token, instance.id, "stopped")
                        }
                    }
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cold start recovery preserves instances`() {
        val tempDir = Files.createTempDirectory("conduit-e2e-cold")
        try {
            val dataDir = DataDirectory(tempDir)
            var instanceId = ""

            testApplication {
                val store = InstanceStore(dataDir)
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val instance = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Persist Test", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()
                instanceId = instance.id
                waitUntilReady(client, token, instanceId)
            }

            assertTrue(instanceId.isNotEmpty())
            assertTrue(dataDir.instanceMetadataPath(instanceId).exists())

            // Simulate cold start: delete in-memory tokens. Token persistence is tested
            // separately in TokenStorePersistenceTest.
            dataDir.tokensPath.toFile().delete()

            testApplication {
                val store = InstanceStore(dataDir)
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val recovered = client.get("/api/v1/instances/$instanceId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body<InstanceSummary>()
                assertEquals("Persist Test", recovered.name)
                assertEquals(InstanceState.STOPPED, recovered.state)
                assertEquals("1.20.4", recovered.mcVersion)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `server process reaches running state via Done detection`() = runWithMockServer("Run Test") { (client, token, id) ->
        val status = client.get("/api/v1/instances/$id/server/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ServerStatusResponse>()
        assertEquals(InstanceState.RUNNING, status.state)
        assertTrue(status.uptime >= 0)
    }

    @Test
    fun `console output streams via WebSocket`() = runWithMockServer("WS Console Test", skipStart = true) { (client, token, id) ->
        val ws = wsClient()
        ws.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"subscribe","instanceId":"$id","channels":["console"]}"""))

            client.post("/api/v1/instances/$id/server/start") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val consoleLines = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                val frame = incoming.tryReceive().getOrNull() as? Frame.Text ?: run {
                    delay(100)
                    continue
                }
                val msg = Json.parseToJsonElement(frame.readText()).jsonObject
                if (msg["type"]?.jsonPrimitive?.content == WsMessage.CONSOLE_OUTPUT) {
                    val line = msg["payload"]?.jsonObject?.get("line")?.jsonPrimitive?.content ?: ""
                    consoleLines.add(line)
                    if (line.contains("Done")) break
                }
            }

            assertTrue(consoleLines.any { it.contains("Done") }, "Expected 'Done' in console output, got: $consoleLines")
        }
    }

    @Test
    fun `command output received via WebSocket`() = runWithMockServer("WS Cmd Test") { (client, token, id) ->
        val ws = wsClient()
        ws.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"subscribe","instanceId":"$id","channels":["console"]}"""))
            delay(200)

            client.post("/api/v1/instances/$id/server/command") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(SendCommandRequest(command = "list"))
            }

            val consoleLines = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val frame = incoming.tryReceive().getOrNull() as? Frame.Text ?: run {
                    delay(100)
                    continue
                }
                val msg = Json.parseToJsonElement(frame.readText()).jsonObject
                if (msg["type"]?.jsonPrimitive?.content == WsMessage.CONSOLE_OUTPUT) {
                    val line = msg["payload"]?.jsonObject?.get("line")?.jsonPrimitive?.content ?: ""
                    consoleLines.add(line)
                    if (line.contains("players online")) break
                }
            }

            assertTrue(consoleLines.any { it.contains("players online") }, "Expected player count in output, got: $consoleLines")
        }
    }

    @Test
    fun `console input sent via WebSocket reaches process`() = runWithMockServer("WS Input Test") { (client, token, id) ->
        val ws = wsClient()
        ws.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"subscribe","instanceId":"$id","channels":["console"]}"""))
            delay(200)

            send(Frame.Text("""{"type":"console.input","instanceId":"$id","payload":{"command":"list"},"timestamp":"2026-05-01T00:00:00Z"}"""))

            val consoleLines = mutableListOf<String>()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val frame = incoming.tryReceive().getOrNull() as? Frame.Text ?: run {
                    delay(100)
                    continue
                }
                val msg = Json.parseToJsonElement(frame.readText()).jsonObject
                if (msg["type"]?.jsonPrimitive?.content == WsMessage.CONSOLE_OUTPUT) {
                    val line = msg["payload"]?.jsonObject?.get("line")?.jsonPrimitive?.content ?: ""
                    consoleLines.add(line)
                    if (line.contains("players online")) break
                }
            }

            assertTrue(consoleLines.any { it.contains("players online") }, "Expected player count from WS-sent command, got: $consoleLines")
        }
    }

    @Test
    fun `graceful stop terminates process`() = runWithMockServer("Stop Test") { (client, token, id) ->
        val stopResp = client.post("/api/v1/instances/$id/server/stop") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<ServerStatusResponse>()
        assertEquals(InstanceState.STOPPING, stopResp.state)

        val stoppedStatus = pollUntilState(client, token, id, "stopped")
        assertEquals(InstanceState.STOPPED, stoppedStatus.state)

        val errorResp = client.post("/api/v1/instances/$id/server/stop") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, errorResp.status)
    }
}
