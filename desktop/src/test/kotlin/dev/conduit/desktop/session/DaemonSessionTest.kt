package dev.conduit.desktop.session

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DaemonSessionTest {

    private fun mockApiClient(): dev.conduit.core.api.ConduitApiClient {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        return mockApiClient(httpClient)
    }

    private fun createSession(): DaemonSession =
        DaemonSession(
            daemonId = "test-daemon",
            daemonName = "Test Daemon",
            daemonUrl = "http://localhost:9147",
            apiClient = mockApiClient(),
        )

    private val testScope = CoroutineScope(SupervisorJob())

    @Test
    fun `isActive is false before start`() {
        val session = createSession()
        assertFalse(session.isActive)
    }

    @Test
    fun `isActive is true after start`() {
        val session = createSession()
        session.start("test-token", testScope)
        assertTrue(session.isActive)
    }

    @Test
    fun `start returns wsClient and sets isActive`() {
        val session = createSession()
        val wsClient = session.start("test-token", testScope)
        assertNotNull(wsClient)
        assertTrue(session.isActive)
    }

    @Test
    fun `getConsoleLines returns empty by default`() = runBlocking {
        val session = createSession()
        val lines = session.getConsoleLines("inst-1").first()
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `appendConsoleLine adds to buffer`() = runBlocking {
        val session = createSession()
        session.appendConsoleLine("inst-1", "line1")
        session.appendConsoleLine("inst-1", "line2")

        val lines = session.getConsoleLines("inst-1").first()
        assertEquals(listOf("line1", "line2"), lines)
    }

    @Test
    fun `console buffers are per-instance`() = runBlocking {
        val session = createSession()
        session.appendConsoleLine("a", "a1")
        session.appendConsoleLine("b", "b1")
        session.appendConsoleLine("a", "a2")

        assertEquals(listOf("a1", "a2"), session.getConsoleLines("a").first())
        assertEquals(listOf("b1"), session.getConsoleLines("b").first())
    }

    @Test
    fun `console buffer persists across getConsoleLines calls`() = runBlocking {
        val session = createSession()
        session.appendConsoleLine("inst-1", "hello")

        assertEquals(listOf("hello"), session.getConsoleLines("inst-1").first())
        assertEquals(listOf("hello"), session.getConsoleLines("inst-1").first())
    }

    @Test
    fun `stop clears state`() = runBlocking {
        val session = createSession()
        session.start("token", testScope)
        session.appendConsoleLine("inst-1", "hello")

        session.stop()

        assertFalse(session.isActive)
        assertTrue(session.getConsoleLines("inst-1").first().isEmpty())
    }
}
