package dev.conduit.daemon

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitApiException
import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.InstanceState
import dev.conduit.daemon.service.DataDirectory
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class ConduitApiClientTest {

    private fun withDaemonAndClient(
        block: suspend (client: ConduitApiClient) -> Unit,
    ) {
        val tempDir = Files.createTempDirectory("conduit-test")
        tempDir.toFile().deleteOnExit()
        val server = embeddedServer(Netty, port = 0) {
            module(dataDirectory = DataDirectory(tempDir))
        }
        server.start(wait = false)
        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }
            val client = ConduitApiClient("http://localhost:$port")
            try {
                runBlocking { block(client) }
            } finally {
                client.close()
            }
        } finally {
            server.stop(100, 200)
        }
    }

    private suspend fun pairClient(client: ConduitApiClient): String {
        val code = client.initiatePairing().code
        val response = client.confirmPairing(code, "Test Desktop")
        client.setToken(response.token)
        return response.token
    }

    @Test
    fun `health returns ok`() = withDaemonAndClient { client ->
        val health = client.health()
        assertEquals("ok", health.status)
        assertNotNull(health.conduitVersion)
    }

    @Test
    fun `pairing flow works end to end`() = withDaemonAndClient { client ->
        val initResponse = client.initiatePairing()
        assertEquals(6, initResponse.code.length)

        val confirmResponse = client.confirmPairing(initResponse.code, "Test Desktop")
        assertTrue(confirmResponse.token.startsWith("conduit_"))
        assertNotNull(confirmResponse.daemonId)
    }

    @Test
    fun `list instances after pairing returns empty list`() = withDaemonAndClient { client ->
        pairClient(client)
        val instances = client.listInstances()
        assertTrue(instances.isEmpty())
    }

    @Test
    fun `create instance returns initializing state`() = withDaemonAndClient { client ->
        pairClient(client)
        val instance = client.createInstance(
            CreateInstanceRequest(
                name = "Test Server",
                mcVersion = "1.20.4",
                description = "A test server",
            ),
        )
        assertEquals("Test Server", instance.name)
        assertEquals("1.20.4", instance.mcVersion)
        assertEquals("A test server", instance.description)
        assertEquals(InstanceState.INITIALIZING, instance.state)
        assertNotNull(instance.taskId)
        assertEquals(25565, instance.mcPort)
    }

    @Test
    fun `unauthenticated request throws exception`() = withDaemonAndClient { client ->
        val code = client.initiatePairing().code
        client.confirmPairing(code, "Test Desktop")

        val ex = assertFailsWith<ConduitApiException> {
            client.listInstances()
        }
        assertEquals(401, ex.httpStatus)
    }
}
