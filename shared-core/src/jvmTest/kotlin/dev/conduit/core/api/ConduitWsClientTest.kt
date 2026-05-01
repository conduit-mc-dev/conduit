@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.conduit.core.api

import dev.conduit.core.model.WsConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class ConduitWsClientTest {

    @Test
    fun `connectionState starts as DISCONNECTED`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")
        assertEquals(WsConnectionState.DISCONNECTED, client.connectionState.value)
        client.close()
    }

    @Test
    fun `subscribe remembers channels when disconnected`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.subscribe("inst-1", listOf("console"))
        client.subscribe("inst-2", listOf("console", "stats"))

        assertEquals(2, client.pendingSubscriptionCount)
        assertEquals(
            setOf("inst-1" to setOf("console"), "inst-2" to setOf("console", "stats")),
            client.pendingSubscriptions
        )
        client.close()
    }

    @Test
    fun `unsubscribe when disconnected removes pending subscription`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.subscribe("inst-1", listOf("console"))
        client.unsubscribe("inst-1", listOf("console"))

        assertEquals(0, client.pendingSubscriptionCount)
        client.close()
    }

    @Test
    fun `subscribe overwrites previous channels for same instance`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.subscribe("inst-1", listOf("console"))
        client.subscribe("inst-1", listOf("console", "stats"))

        assertEquals(1, client.pendingSubscriptionCount)
        assertEquals(
            setOf("inst-1" to setOf("console", "stats")),
            client.pendingSubscriptions
        )
        client.close()
    }

    @Test
    fun `connectionState transitions through CONNECTING when connect is called`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        val states = mutableListOf<WsConnectionState>()
        val job = backgroundScope.launch {
            client.connectionState.collect { states.add(it) }
        }

        // Connect to a non-existent server — CONNECTING is set synchronously before the CIO call
        client.connect(backgroundScope)
        runCurrent()

        assertTrue(states.isNotEmpty(), "Expected at least one state transition but got none")
        assertEquals(WsConnectionState.DISCONNECTED, states.first(), "Expected DISCONNECTED as first state")
        assertTrue(
            states.contains(WsConnectionState.CONNECTING),
            "Expected CONNECTING state but got $states"
        )

        client.close()
        job.cancel()
    }

    @Test
    fun `subscribe with empty channels is still remembered`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.subscribe("inst-1", emptyList())

        assertEquals(1, client.pendingSubscriptionCount)
        assertEquals(
            setOf("inst-1" to emptySet<String>()),
            client.pendingSubscriptions
        )
        client.close()
    }

    @Test
    fun `unsubscribe from non-existent instance does not throw`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.unsubscribe("inst-99", listOf("console"))

        assertEquals(0, client.pendingSubscriptionCount)
        client.close()
    }

    @Test
    fun `partial unsubscribe removes only matching subscription`() = runTest {
        val client = ConduitWsClient("http://localhost:9999", "test-token")

        client.subscribe("inst-1", listOf("console"))
        client.subscribe("inst-2", listOf("console", "stats"))

        client.unsubscribe("inst-1", listOf("console"))

        assertEquals(1, client.pendingSubscriptionCount)
        assertEquals(
            setOf("inst-2" to setOf("console", "stats")),
            client.pendingSubscriptions
        )
        client.close()
    }
}
