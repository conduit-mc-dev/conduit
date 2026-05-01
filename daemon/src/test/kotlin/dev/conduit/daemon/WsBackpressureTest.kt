package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
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
import kotlin.test.*

/**
 * WebSocket backpressure smoke test.
 *
 * Under rapid console output (e.g., mod loading spam), the WebSocket connection
 * must stay alive and deliver messages without dropping. This test starts a mock
 * MC server that produces sustained high-volume output and verifies:
 *
 * 1. The WS subscription receives lines continuously over 5+ seconds.
 * 2. The connection survives without handler exceptions.
 * 3. At least the expected minimum number of lines arrives.
 */
class WsBackpressureTest {

    private fun rapidServerJvmConfig(): Pair<String, List<String>> {
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        return Pair(javaPath, listOf("-cp", classpath, RapidOutputMcServer::class.qualifiedName!!))
    }

    @Test
    fun `sustained high-volume console output over 5 seconds`() {
        val tempDir = Files.createTempDirectory("conduit-ws-bp")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = rapidServerJvmConfig()

            testApplication {
                application {
                    module(
                        dataDirectory = dataDir,
                        instanceStore = store,
                        mojangClient = createMockMojangClient(),
                    )
                }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val instance = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "WS Backpressure", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                // Wait for init (server jar download mock is instant).
                val initDeadline = System.currentTimeMillis() + 10_000
                while (store.get(instance.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline
                ) {
                    delay(100)
                }

                client.put("/api/v1/instances/${instance.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }

                store.updateJvmConfig(instance.id, true, jvmArgs, true, javaPath)

                // Open WS first, then start the server, so we catch the output from t=0.
                val ws = wsClient()
                ws.webSocket("/api/v1/ws?token=$token") {
                    send(Frame.Text("""{"type":"subscribe","instanceId":"${instance.id}","channels":["console"]}"""))
                    delay(200)

                    // Start the server — RapidOutputMcServer prints "Done" immediately
                    // then begins sustained background output.
                    client.post("/api/v1/instances/${instance.id}/server/start") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }

                    // Wait until RUNNING (the "Done" line).
                    val runningDeadline = System.currentTimeMillis() + 10_000
                    var reached = false
                    while (System.currentTimeMillis() < runningDeadline) {
                        val s = client.get("/api/v1/instances/${instance.id}/server/status") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }.body<ServerStatusResponse>()
                        if (s.state == InstanceState.RUNNING) {
                            reached = true
                            break
                        }
                        delay(200)
                    }
                    assertTrue(reached, "Server did not reach RUNNING state within timeout")

                    // Collect console output for 5 seconds to verify the WS survives
                    // sustained high-volume traffic.
                    val consoleLines = mutableListOf<String>()
                    val collectDeadline = System.currentTimeMillis() + 5_000
                    var emptyPolls = 0
                    while (System.currentTimeMillis() < collectDeadline) {
                        val result = incoming.tryReceive()
                        val frame = result.getOrNull() as? Frame.Text
                        if (frame == null) {
                            emptyPolls++
                            if (emptyPolls > 500) fail("WebSocket appears dead: ${emptyPolls} consecutive empty polls")
                            delay(10)
                            continue
                        }
                        emptyPolls = 0
                        val msg = Json.parseToJsonElement(frame.readText()).jsonObject
                        if (msg["type"]?.jsonPrimitive?.content == WsMessage.CONSOLE_OUTPUT) {
                            val line = msg["payload"]?.jsonObject?.get("line")?.jsonPrimitive?.content ?: ""
                            consoleLines.add(line)
                        }
                    }

                    // Stop the server cleanly.
                    runCatching {
                        client.post("/api/v1/instances/${instance.id}/server/stop") {
                            header(HttpHeaders.Authorization, "Bearer $token")
                        }
                    }

                    // Allow cleanup broadcasts to arrive.
                    delay(200)

                    // Assertions.
                    assertTrue(consoleLines.isNotEmpty(), "Expected console output, got none")
                    assertTrue(
                        consoleLines.size >= 50,
                        "Expected >= 50 console lines in 5s under sustained output, got ${consoleLines.size}",
                    )
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
