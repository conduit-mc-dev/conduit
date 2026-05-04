package dev.conduit.desktop.session

import kotlin.test.*
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class DaemonManagerTest {

    private fun managerWithTempDir(): Pair<DaemonManager, Path> {
        val dir = createTempDirectory("conduit-test")
        return DaemonManager(configDir = dir) to dir
    }

    @Test
    fun `loadSavedSession returns null when no session file exists`() {
        val (manager, _) = managerWithTempDir()
        assertNull(manager.loadSavedSession())
    }

    @Test
    fun `save and load savedSession roundtrip`() {
        val (manager, _) = managerWithTempDir()
        manager.saveSession("http://localhost:9147", "conduit_token_abc123", "d1", "My Daemon")

        val saved = manager.loadSavedSession()
        assertNotNull(saved)
        assertEquals("http://localhost:9147", saved.daemonUrl)
        assertEquals("conduit_token_abc123", saved.token)
        assertEquals("d1", saved.daemonId)
        assertEquals("My Daemon", saved.daemonName)
    }

    @Test
    fun `clearSession removes saved session`() {
        val (manager, _) = managerWithTempDir()
        manager.saveSession("http://localhost:9147", "token", "d1", "Test")
        assertNotNull(manager.loadSavedSession())

        manager.clearSession()
        assertNull(manager.loadSavedSession())
    }

    @Test
    fun `loadSavedSession returns null for corrupt file`() {
        val (manager, dir) = managerWithTempDir()
        val sessionFile = dir.resolve("session.json").toFile()
        sessionFile.parentFile.mkdirs()
        sessionFile.writeText("{ not valid json }")

        assertNull(manager.loadSavedSession())
    }

    @Test
    fun `loadSavedSession returns null for file with missing fields`() {
        val (manager, dir) = managerWithTempDir()
        val sessionFile = dir.resolve("session.json").toFile()
        sessionFile.parentFile.mkdirs()
        sessionFile.writeText("""{"daemonUrl":"http://x"}""")

        // token is blank → returns null
        assertNull(manager.loadSavedSession())
    }
}
