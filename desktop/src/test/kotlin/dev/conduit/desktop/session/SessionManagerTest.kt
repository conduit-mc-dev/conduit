package dev.conduit.desktop.session

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class SessionManagerTest {

    private fun mockApiClient(): dev.conduit.core.api.ConduitApiClient {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        return mockApiClient(httpClient)
    }

    private fun sessionWithTempDir(): Pair<SessionManager, Path> {
        val dir = createTempDirectory("conduit-test")
        return SessionManager(mockApiClient(), configDir = dir) to dir
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

    // --- Persistence ---

    @Test
    fun `loadSavedSession returns null when no session file exists`() {
        val (session, _) = sessionWithTempDir()
        assertNull(session.loadSavedSession())
    }

    @Test
    fun `save and load savedSession roundtrip`() {
        val (session, _) = sessionWithTempDir()
        session.saveSession("http://localhost:9147", "conduit_token_abc123")

        val saved = session.loadSavedSession()
        assertNotNull(saved)
        assertEquals("http://localhost:9147", saved.daemonUrl)
        assertEquals("conduit_token_abc123", saved.token)
    }

    @Test
    fun `clearSession removes saved session`() {
        val (session, _) = sessionWithTempDir()
        session.saveSession("http://localhost:9147", "token")
        assertNotNull(session.loadSavedSession())

        session.clearSession()
        assertNull(session.loadSavedSession())
    }

    @Test
    fun `loadSavedSession returns null for corrupt file`() {
        val (session, dir) = sessionWithTempDir()
        val sessionFile = dir.resolve("session.json").toFile()
        sessionFile.parentFile.mkdirs()
        sessionFile.writeText("{ not valid json }")

        assertNull(session.loadSavedSession())
    }

    @Test
    fun `loadSavedSession returns null for file with missing fields`() {
        val (session, dir) = sessionWithTempDir()
        val sessionFile = dir.resolve("session.json").toFile()
        sessionFile.parentFile.mkdirs()
        sessionFile.writeText("""{"daemonUrl":"http://x"}""")

        assertNull(session.loadSavedSession())
    }
}
