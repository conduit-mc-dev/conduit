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

    private fun createMockServerScript(dir: Path): Path {
        val script = dir.resolve("mock-mc-server.sh")
        script.writeText("""
            #!/bin/bash
            echo "[main/INFO]: Starting minecraft server version 1.20.4"
            echo "[Server thread/INFO]: Preparing level \"world\""
            echo "[Server thread/INFO]: Done (1.0s)! For help, type \"help\""
            while IFS= read -r line; do
                case "${'$'}line" in
                    list) echo "[Server thread/INFO]: There are 0 of a max of 20 players online:" ;;
                    say\ *) echo "[Server thread/INFO]: [Server] ${'$'}{line#say }" ;;
                    stop) echo "[Server thread/INFO]: Stopping the server"; echo "[Server thread/INFO]: Saving worlds"; exit 0 ;;
                    *) echo "[Server thread/INFO]: Unknown or empty command. Type \"help\" for help." ;;
                esac
            done
        """.trimIndent())
        script.toFile().setExecutable(true)
        return script
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
            val script = createMockServerScript(tempDir)

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

                store.updateJvmConfig(instance.id, true, emptyList(), true, script.toString())

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
    fun `full lifecycle with real server jar`() {
        if (System.getenv("CONDUIT_SLOW_TESTS") == null && System.getProperty("slow") == null) {
            println("Skipping slow test (set CONDUIT_SLOW_TESTS=1 or -Dslow=true to enable)")
            return
        }

        withTempDir("conduit-e2e-slow") { tempDir ->
            testApplication {
                val dataDir = DataDirectory(tempDir)
                application { module(dataDirectory = dataDir, instanceStore = InstanceStore(dataDir)) }

                val client = jsonClient()
                val token = pairAndGetToken(client)

                val instance = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Real JAR Test", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()
                assertEquals(InstanceState.INITIALIZING, instance.state)

                waitUntilReady(client, token, instance.id, timeoutMs = 120_000)
                assertTrue(tempDir.resolve("instances/${instance.id}/server.jar").exists())

                client.put("/api/v1/instances/${instance.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }

                client.post("/api/v1/instances/${instance.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                pollUntilState(client, token, instance.id, "running", timeoutMs = 90_000)

                val cmdResp = client.post("/api/v1/instances/${instance.id}/server/command") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(SendCommandRequest(command = "list"))
                }.body<CommandAcceptedResponse>()
                assertTrue(cmdResp.accepted)

                client.post("/api/v1/instances/${instance.id}/server/stop") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                pollUntilState(client, token, instance.id, "stopped", timeoutMs = 30_000)

                val delResp = client.delete("/api/v1/instances/${instance.id}") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.NoContent, delResp.status)
            }
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
