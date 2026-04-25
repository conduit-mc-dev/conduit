package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServerRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    private fun ApplicationTestBuilder.wsClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
        install(WebSockets)
    }

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    private suspend fun pairAndGetToken(client: io.ktor.client.HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "Test Device"))
        }.body<PairConfirmResponse>().token
    }

    private suspend fun createTestInstance(
        client: io.ktor.client.HttpClient,
        token: String,
        name: String = "test-server",
    ): InstanceSummary {
        return client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = name, mcVersion = "1.20.4"))
        }.body()
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
    fun `start without eula returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/server/start") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)

        val error = response.body<ErrorResponse>()
        // 新实例处于 INITIALIZING 状态，优先返回 INSTANCE_INITIALIZING
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
    fun `send command to stopped server returns 409`() = testApplication {
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
            send(Frame.Text("""{"type":"subscribe","instanceId":"test123","channels":["console","stats"]}"""))
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            assertTrue(incoming.isEmpty)
        }
    }
}
