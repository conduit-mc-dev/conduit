package dev.conduit.daemon

import dev.conduit.daemon.service.neoforgeVersionPrefix
import dev.conduit.daemon.service.parseMavenVersions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoaderVersionMappingTest {

    @Test
    fun `neoforge prefix for patched MC version`() {
        assertEquals("20.4.", neoforgeVersionPrefix("1.20.4"))
        assertEquals("21.1.", neoforgeVersionPrefix("1.21.1"))
    }

    @Test
    fun `neoforge prefix for MC version without patch falls back to zero`() {
        assertEquals("21.0.", neoforgeVersionPrefix("1.21"))
        assertEquals("20.0.", neoforgeVersionPrefix("1.20"))
    }

    @Test
    fun `neoforge prefix rejects non-1 major`() {
        assertNull(neoforgeVersionPrefix("2.0.0"))
        assertNull(neoforgeVersionPrefix("invalid"))
        assertNull(neoforgeVersionPrefix(""))
    }

    @Test
    fun `maven version parser extracts all version nodes`() {
        val xml = """
            <metadata>
              <versioning>
                <latest>1.20.4-49.0.14</latest>
                <release>1.20.4-49.0.14</release>
                <versions>
                  <version>1.20.4-49.0.14</version>
                  <version>1.19.4-45.2.0</version>
                </versions>
              </versioning>
            </metadata>
        """.trimIndent()
        assertEquals(listOf("1.20.4-49.0.14", "1.19.4-45.2.0"), parseMavenVersions(xml))
    }

    @Test
    fun `maven version parser decodes XML entities`() {
        val xml = """
            <metadata><versioning><versions>
              <version>1.0.0&amp;special</version>
            </versions></versioning></metadata>
        """.trimIndent()
        assertEquals(listOf("1.0.0&special"), parseMavenVersions(xml))
    }
}
