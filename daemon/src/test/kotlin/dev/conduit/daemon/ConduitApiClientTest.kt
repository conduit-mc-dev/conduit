package dev.conduit.daemon

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitApiException
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ConduitApiClientTest {

    private fun withDaemonAndClient(
        block: suspend (client: ConduitApiClient) -> Unit,
    ) {
        val server = embeddedServer(Netty, port = 0) { module() }
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
        assertEquals("ok", health["status"])
        assertNotNull(health["conduitVersion"])
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
    fun `list minecraft versions after pairing returns versions`() = withDaemonAndClient { client ->
        pairClient(client)
        val versions = client.listMinecraftVersions()
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.any { it.id == "1.21.5" })
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
