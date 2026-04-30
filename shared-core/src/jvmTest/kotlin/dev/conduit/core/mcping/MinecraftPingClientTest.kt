package dev.conduit.core.mcping

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftPingClientTest {

    private val client = MinecraftPingClient()

    @Test
    fun `ping returns parsed player info on valid response`() = runBlocking {
        val responseJson = """
            {
              "version": { "name": "1.20.4", "protocol": 765 },
              "players": {
                "max": 20,
                "online": 3,
                "sample": [
                  {"name": "Steve", "id": "00000000-0000-0000-0000-000000000001"},
                  {"name": "Alex", "id": "00000000-0000-0000-0000-000000000002"}
                ]
              },
              "description": "Conduit test server"
            }
        """.trimIndent()

        fakeSlpServer(responseJson).use { (port) ->
            val result = client.ping("127.0.0.1", port)
            requireNotNull(result) { "Ping should return non-null on successful response" }
            assertEquals(3, result.onlinePlayers)
            assertEquals(20, result.maxPlayers)
            assertEquals(listOf("Steve", "Alex"), result.sample)
            assertEquals("1.20.4", result.versionName)
            assertEquals(765, result.protocolVersion)
            assertTrue(result.latencyMs >= 0)
        }
    }

    @Test
    fun `ping returns null when server is not listening`() = runBlocking {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val result = client.ping("127.0.0.1", unusedPort, timeoutMs = 500)
        assertNull(result)
    }

    @Test
    fun `ping returns null when response JSON is malformed`() = runBlocking {
        fakeSlpServer("not a valid json {{{").use { (port) ->
            val result = client.ping("127.0.0.1", port)
            assertNull(result)
        }
    }

    @Test
    fun `ping returns empty sample when server omits sample field`() = runBlocking {
        val responseJson = """{"players":{"max":20,"online":0}}"""
        fakeSlpServer(responseJson).use { (port) ->
            val result = client.ping("127.0.0.1", port)
            requireNotNull(result)
            assertEquals(0, result.onlinePlayers)
            assertEquals(20, result.maxPlayers)
            assertEquals(emptyList(), result.sample)
        }
    }

    private data class FakeServer(val port: Int, val socket: ServerSocket, val thread: Thread) : AutoCloseable {
        override fun close() {
            socket.close()
            thread.interrupt()
        }
    }

    /**
     * Spawns a one-shot SLP server on a background daemon thread. Accepts one connection,
     * drains the request bytes (handshake + status request), writes the given response JSON
     * wrapped in a status-response packet, then closes. Returns immediately.
     *
     * Uses a raw Thread instead of coroutines to avoid structured-concurrency deadlocks:
     * a child coroutine blocked on ServerSocket.accept() would keep its parent scope alive
     * forever, because accept() ignores cancellation.
     */
    private fun fakeSlpServer(responseJson: String): FakeServer {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val thread = Thread({
            runCatching {
                server.accept().use { conn ->
                    conn.soTimeout = 3_000
                    val input = DataInputStream(conn.getInputStream())
                    val out = DataOutputStream(conn.getOutputStream())

                    // Drain handshake packet
                    val handshakeLen = client.readVarInt(input)
                    input.readFully(ByteArray(handshakeLen))

                    // Drain status request packet
                    val statusReqLen = client.readVarInt(input)
                    input.readFully(ByteArray(statusReqLen))

                    // Build status response payload: varint packetId(0x00) + varint jsonLen + jsonBytes
                    val payload = ByteArrayOutputStream()
                    val payloadOut = DataOutputStream(payload)
                    client.writeVarInt(payloadOut, 0x00)
                    val jsonBytes = responseJson.toByteArray(Charsets.UTF_8)
                    client.writeVarInt(payloadOut, jsonBytes.size)
                    payloadOut.write(jsonBytes)
                    val payloadBytes = payload.toByteArray()

                    // Frame: varint totalLen + payload
                    val frame = ByteArrayOutputStream()
                    client.writeVarInt(DataOutputStream(frame), payloadBytes.size)
                    frame.write(payloadBytes)
                    out.write(frame.toByteArray())
                    out.flush()
                }
            }
        }, "fake-slp-server-${server.localPort}")
        thread.isDaemon = true
        thread.start()
        return FakeServer(server.localPort, server, thread)
    }
}
