package dev.conduit.daemon

import dev.conduit.core.model.LoaderInfo
import dev.conduit.core.model.LoaderType
import dev.conduit.daemon.service.LaunchTarget
import dev.conduit.daemon.service.neoforgeVersionPrefix
import dev.conduit.daemon.service.parseMavenVersions
import dev.conduit.daemon.service.resolveLaunchTarget
import dev.conduit.daemon.service.validateMcVersionForForge
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `Forge version check accepts MC 1_17 and newer`() {
        validateMcVersionForForge("1.17")
        validateMcVersionForForge("1.17.1")
        validateMcVersionForForge("1.20.4")
        validateMcVersionForForge("1.21.1")
    }

    @Test
    fun `Forge version check rejects MC older than 1_17`() {
        val ex = assertFailsWith<ApiException> { validateMcVersionForForge("1.16.5") }
        assertEquals(HttpStatusCode.UnprocessableEntity, ex.httpStatus)
        assertEquals("UNSUPPORTED_MC_VERSION", ex.code)
        assertFailsWith<ApiException> { validateMcVersionForForge("1.12.2") }
        assertFailsWith<ApiException> { validateMcVersionForForge("1.8") }
    }

    @Test
    fun `Forge version check rejects malformed input`() {
        assertFailsWith<ApiException> { validateMcVersionForForge("invalid") }
        assertFailsWith<ApiException> { validateMcVersionForForge("") }
        assertFailsWith<ApiException> { validateMcVersionForForge("2.0.0") }
        assertFailsWith<ApiException> { validateMcVersionForForge("1.x.y") }
    }

    @Test
    fun `launch target is VanillaJar when no loader installed`() {
        assertEquals(LaunchTarget.VanillaJar, resolveLaunchTarget(null, isWindows = false))
        assertEquals(LaunchTarget.VanillaJar, resolveLaunchTarget(null, isWindows = true))
    }

    @Test
    fun `launch target is VanillaJar for Fabric`() {
        // Fabric installer overwrites server.jar with a ~180KB launcher, so -jar server.jar still applies.
        assertEquals(
            LaunchTarget.VanillaJar,
            resolveLaunchTarget(LoaderInfo(LoaderType.FABRIC, "0.16.14"), isWindows = false),
        )
        assertEquals(
            LaunchTarget.VanillaJar,
            resolveLaunchTarget(LoaderInfo(LoaderType.FABRIC, "0.16.14"), isWindows = true),
        )
    }

    @Test
    fun `launch target is LoaderJar quilt-server-launch_jar for Quilt`() {
        // Quilt installer emits a separate quilt-server-launch.jar and keeps vanilla server.jar intact;
        // its runtime main class reads server.jar as the game jar.
        val expected = LaunchTarget.LoaderJar("quilt-server-launch.jar")
        assertEquals(expected, resolveLaunchTarget(LoaderInfo(LoaderType.QUILT, "0.20.0-beta.9"), isWindows = false))
        assertEquals(expected, resolveLaunchTarget(LoaderInfo(LoaderType.QUILT, "0.20.0-beta.9"), isWindows = true))
    }

    @Test
    fun `launch target is Forge unix argfile on non-Windows`() {
        val target = resolveLaunchTarget(
            LoaderInfo(LoaderType.FORGE, "1.20.4-49.0.14"),
            isWindows = false,
        )
        assertEquals(
            LaunchTarget.ArgFile("libraries/net/minecraftforge/forge/1.20.4-49.0.14/unix_args.txt"),
            target,
        )
    }

    @Test
    fun `launch target is Forge Windows argfile on Windows`() {
        val target = resolveLaunchTarget(
            LoaderInfo(LoaderType.FORGE, "1.20.4-49.0.14"),
            isWindows = true,
        )
        assertEquals(
            LaunchTarget.ArgFile("libraries/net/minecraftforge/forge/1.20.4-49.0.14/win_args.txt"),
            target,
        )
    }

    @Test
    fun `launch target is NeoForge unix argfile on non-Windows`() {
        val target = resolveLaunchTarget(
            LoaderInfo(LoaderType.NEOFORGE, "21.0.167"),
            isWindows = false,
        )
        assertEquals(
            LaunchTarget.ArgFile("libraries/net/neoforged/neoforge/21.0.167/unix_args.txt"),
            target,
        )
    }

    @Test
    fun `launch target is NeoForge Windows argfile on Windows`() {
        val target = resolveLaunchTarget(
            LoaderInfo(LoaderType.NEOFORGE, "21.0.167"),
            isWindows = true,
        )
        assertEquals(
            LaunchTarget.ArgFile("libraries/net/neoforged/neoforge/21.0.167/win_args.txt"),
            target,
        )
    }
}
