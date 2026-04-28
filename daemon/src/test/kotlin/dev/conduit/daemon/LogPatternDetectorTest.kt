package dev.conduit.daemon

import dev.conduit.daemon.service.LogEventType
import dev.conduit.daemon.service.LogPatternDetector
import kotlin.test.*

class LogPatternDetectorTest {

    private val detector = LogPatternDetector()

    private fun loadLog(name: String): String = loadFixture("logs/$name").trim()

    @Test
    fun `detects Done from vanilla server`() {
        val event = detector.detect(loadLog("done_vanilla.txt"))
        assertNotNull(event)
        assertEquals(LogEventType.SERVER_DONE, event.type)
    }

    @Test
    fun `detects Done from Fabric server`() {
        val event = detector.detect(loadLog("done_fabric.txt"))
        assertNotNull(event)
        assertEquals(LogEventType.SERVER_DONE, event.type)
    }

    @Test
    fun `detects OOM error`() {
        val event = detector.detect(loadLog("oom.txt"))
        assertNotNull(event)
        assertEquals(LogEventType.OOM, event.type)
    }

    @Test
    fun `detects port conflict - FAILED TO BIND`() {
        val event = detector.detect(loadLog("port_conflict_bind.txt"))
        assertNotNull(event)
        assertEquals(LogEventType.PORT_CONFLICT, event.type)
    }

    @Test
    fun `detects port conflict - Address already in use`() {
        val event = detector.detect(loadLog("port_conflict_address.txt"))
        assertNotNull(event)
        assertEquals(LogEventType.PORT_CONFLICT, event.type)
    }

    @Test
    fun `detects crash report`() {
        val line = loadLog("crash_report.txt").lines().first()
        val event = detector.detect(line)
        assertNotNull(event)
        assertEquals(LogEventType.CRASH, event.type)
    }

    @Test
    fun `returns null for normal log lines`() {
        val event = detector.detect(loadLog("normal_startup.txt"))
        assertNull(event)
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(detector.detect(""))
    }

    @Test
    fun `returns null for plain text`() {
        assertNull(detector.detect("Hello World"))
    }
}
