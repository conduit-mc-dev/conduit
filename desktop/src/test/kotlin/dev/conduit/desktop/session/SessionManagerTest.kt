package dev.conduit.desktop.session

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SessionManagerTest {

    private fun mockApiClient(): dev.conduit.core.api.ConduitApiClient {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        return mockApiClient(httpClient)
    }

    @Test
    fun `isActive is false before start`() {
        val session = SessionManager(mockApiClient())
        assertFalse(session.isActive)
    }

    @Test
    fun `isActive is true after start`() {
        val session = SessionManager(mockApiClient())
        session.start("test-token")
        assertTrue(session.isActive)
    }

    @Test
    fun `start returns wsClient and sets isActive`() {
        val session = SessionManager(mockApiClient())
        val wsClient = session.start("test-token")
        assertNotNull(wsClient)
        assertTrue(session.isActive)
    }

    @Test
    fun `getConsoleLines returns empty by default`() = runBlocking {
        val session = SessionManager(mockApiClient())
        val lines = session.getConsoleLines("inst-1").first()
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `appendConsoleLine adds to buffer`() = runBlocking {
        val session = SessionManager(mockApiClient())
        session.appendConsoleLine("inst-1", "line1")
        session.appendConsoleLine("inst-1", "line2")

        val lines = session.getConsoleLines("inst-1").first()
        assertEquals(listOf("line1", "line2"), lines)
    }

    @Test
    fun `console buffers are per-instance`() = runBlocking {
        val session = SessionManager(mockApiClient())
        session.appendConsoleLine("a", "a1")
        session.appendConsoleLine("b", "b1")
        session.appendConsoleLine("a", "a2")

        assertEquals(listOf("a1", "a2"), session.getConsoleLines("a").first())
        assertEquals(listOf("b1"), session.getConsoleLines("b").first())
    }

    @Test
    fun `console buffer persists across getConsoleLines calls`() = runBlocking {
        val session = SessionManager(mockApiClient())
        session.appendConsoleLine("inst-1", "hello")

        // First access
        assertEquals(listOf("hello"), session.getConsoleLines("inst-1").first())

        // Access again - should still have the data
        assertEquals(listOf("hello"), session.getConsoleLines("inst-1").first())
    }

    @Test
    fun `stop clears state`() = runBlocking {
        val session = SessionManager(mockApiClient())
        session.start("token")
        session.appendConsoleLine("inst-1", "hello")

        session.stop()

        assertFalse(session.isActive)
        assertTrue(session.getConsoleLines("inst-1").first().isEmpty())
    }
}
