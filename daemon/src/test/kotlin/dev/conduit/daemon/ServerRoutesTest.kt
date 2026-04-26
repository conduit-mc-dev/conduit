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
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerRoutesTest {

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    private lateinit var instanceStore: InstanceStore

    private fun testModuleWithStore(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            instanceStore = InstanceStore()
            module(instanceStore = instanceStore, dataDirectory = DataDirectory(tempDir))
        }
    }

    @Test
    fun `eula check returns not accepted for new instance`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/server/eula") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val eula = response.body<EulaResponse>()
        assertFalse(eula.accepted)
        assertEquals("https://aka.ms/MinecraftEULA", eula.eulaUrl)
    }

    @Test
    fun `accept eula then check`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val acceptResponse = client.put("/api/v1/instances/${instance.id}/server/eula") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AcceptEulaRequest(accepted = true))
        }
        assertEquals(HttpStatusCode.OK, acceptResponse.status)
        assertTrue(acceptResponse.body<EulaResponse>().accepted)

        val checkResponse = client.get("/api/v1/instances/${instance.id}/server/eula") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertTrue(checkResponse.body<EulaResponse>().accepted)
    }

    @Test
    fun `start when initializing returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("INSTANCE_INITIALIZING", error.error.code)
    }

    @Test
    fun `server status endpoint returns full status`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/server/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val status = response.body<ServerStatusResponse>()
        assertEquals(InstanceState.INITIALIZING, status.state)
        assertEquals("1.20.4", status.mcVersion)
        assertEquals(0, status.playerCount)
        assertEquals(20, status.maxPlayers)
        assertTrue(status.players.isEmpty())
        assertEquals(0, status.uptime)
    }

    @Test
    fun `server routes require authentication`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/test123/server/eula")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `send command when not running returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/command") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SendCommandRequest(command = "list"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("SERVER_NOT_RUNNING", error.error.code)
    }

    // --- stop/kill/restart error paths ---

    @Test
    fun `stop server when initializing returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/stop") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("INSTANCE_INITIALIZING", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `stop server when stopped returns 409`() = testApplication {
        testModuleWithStore()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)
        instanceStore.markInitialized(instance.id)

        val response = client.post("/api/v1/instances/${instance.id}/server/stop") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("SERVER_NOT_RUNNING", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `kill server when initializing force-stops and returns 200`() = testApplication {
        testModuleWithStore()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/kill") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val status = response.body<ServerStatusResponse>()
        assertEquals(InstanceState.STOPPED, status.state)
    }

    @Test
    fun `kill server when stopped returns 409`() = testApplication {
        testModuleWithStore()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)
        instanceStore.markInitialized(instance.id)

        val response = client.post("/api/v1/instances/${instance.id}/server/kill") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("SERVER_NOT_RUNNING", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `restart server when initializing without eula returns eula error`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/restart") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("EULA_NOT_ACCEPTED", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `restart server when initializing with eula returns 409`() = testApplication {
        testModuleWithStore()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        client.put("/api/v1/instances/${instance.id}/server/eula") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AcceptEulaRequest(accepted = true))
        }

        val response = client.post("/api/v1/instances/${instance.id}/server/restart") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("INSTANCE_INITIALIZING", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `restart server when stopped without eula returns 409`() = testApplication {
        testModuleWithStore()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)
        instanceStore.markInitialized(instance.id)

        val response = client.post("/api/v1/instances/${instance.id}/server/restart") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("EULA_NOT_ACCEPTED", response.body<ErrorResponse>().error.code)
    }

    // --- WebSocket ---

    @Test
    fun `websocket connection with invalid token is rejected`() = testApplication {
        testModule()()
        val client = wsClient()

        client.webSocket("/api/v1/ws?token=invalid_token") {
            val reason = closeReason.await()
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        }
    }

    @Test
    fun `websocket connection without token is rejected`() = testApplication {
        testModule()()
        val client = wsClient()

        client.webSocket("/api/v1/ws") {
            val reason = closeReason.await()
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        }
    }

    @Test
    fun `websocket connection with valid token succeeds`() = testApplication {
        testModule()()
        val jsonClient = jsonClient()
        val token = pairAndGetToken(jsonClient)
        val client = wsClient()

        client.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"${WsMessage.SUBSCRIBE}","instanceId":"test123","channels":["${WsMessage.CHANNEL_CONSOLE}","${WsMessage.CHANNEL_STATS}"]}"""))
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            assertTrue(incoming.isEmpty)
        }
    }

    @Test
    fun `websocket ping receives pong`() = testApplication {
        testModule()()
        val jsonClient = jsonClient()
        val token = pairAndGetToken(jsonClient)
        val client = wsClient()

        client.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"${WsMessage.PING}"}"""))
            val response = incoming.receive() as Frame.Text
            assertTrue(response.readText().contains(WsMessage.PONG))
        }
    }

    @Test
    fun `websocket receives instance created event`() = testApplication {
        testModule()()
        val jsonClient = jsonClient()
        val token = pairAndGetToken(jsonClient)
        val client = wsClient()

        client.webSocket("/api/v1/ws?token=$token") {
            createTestInstance(jsonClient, token)

            val response = incoming.receive() as Frame.Text
            assertTrue(response.readText().contains(WsMessage.INSTANCE_CREATED))
        }
    }

    @Test
    fun `websocket unsubscribe does not close connection`() = testApplication {
        testModule()()
        val jsonClient = jsonClient()
        val token = pairAndGetToken(jsonClient)
        val client = wsClient()

        client.webSocket("/api/v1/ws?token=$token") {
            send(Frame.Text("""{"type":"${WsMessage.SUBSCRIBE}","instanceId":"test","channels":["${WsMessage.CHANNEL_CONSOLE}"]}"""))
            send(Frame.Text("""{"type":"${WsMessage.UNSUBSCRIBE}","instanceId":"test","channels":["${WsMessage.CHANNEL_CONSOLE}"]}"""))
            send(Frame.Text("""{"type":"${WsMessage.PING}"}"""))
            val response = incoming.receive() as Frame.Text
            assertTrue(response.readText().contains(WsMessage.PONG))
        }
    }
}
