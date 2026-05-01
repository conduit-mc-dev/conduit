package dev.conduit.daemon

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.model.LoaderType
import dev.conduit.core.model.ModEnvSupport
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.ModService
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModMetadataParseTest {

    private val dataDirectory = DataDirectory()
    private val modService = ModService(
        modStore = ModStore(),
        modrinthClient = ModrinthClient(),
        instanceStore = InstanceStore(dataDirectory),
        dataDirectory = dataDirectory,
        broadcaster = WsBroadcaster(AppJson),
        json = AppJson,
    )

    private fun createJar(vararg entries: Pair<String, String>): Path {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        val tmp = java.nio.file.Files.createTempFile("test-mod-", ".jar")
        tmp.toFile().deleteOnExit()
        tmp.writeBytes(buffer.toByteArray())
        return tmp
    }

    @Test
    fun `parse fabric mod json extracts name version loader env`() {
        val jarPath = createJar(
            "fabric.mod.json" to """
                {
                    "schemaVersion": 1,
                    "id": "example-mod",
                    "name": "Example Mod",
                    "version": "1.0.0",
                    "environment": "*"
                }
            """.trimIndent(),
        )

        val result = modService.parseModMetadata(jarPath)

        assertEquals("Example Mod", result.name)
        assertEquals("1.0.0", result.version)
        assertEquals(listOf(LoaderType.FABRIC, LoaderType.QUILT), result.loaders)
        assertEquals(ModEnvSupport(client = "required", server = "required"), result.env)
    }

    @Test
    fun `parse quilt mod json extracts nested loader metadata`() {
        val jarPath = createJar(
            "quilt.mod.json" to """
                {
                    "schema_version": 1,
                    "quilt_loader": {
                        "id": "quilt-mod",
                        "version": "2.0.0",
                        "metadata": {
                            "name": "Quilt Mod"
                        }
                    }
                }
            """.trimIndent(),
        )

        val result = modService.parseModMetadata(jarPath)

        assertEquals("Quilt Mod", result.name)
        assertEquals("2.0.0", result.version)
        assertEquals(listOf(LoaderType.QUILT, LoaderType.FABRIC), result.loaders)
    }

    @Test
    fun `parse META-INF mods toml extracts Forge mod metadata`() {
        val jarPath = createJar(
            "META-INF/mods.toml" to """
                modLoader = "javafml"
                loaderVersion = "[1,)"

                [[mods]]
                modId = "examplemod"
                displayName = "Example Forge Mod"
                version = "3.0.0"
                side = "BOTH"
            """.trimIndent(),
        )

        val result = modService.parseModMetadata(jarPath)

        assertEquals("Example Forge Mod", result.name)
        assertEquals("3.0.0", result.version)
        assertEquals(listOf(LoaderType.FORGE, LoaderType.NEOFORGE), result.loaders)
        assertEquals(ModEnvSupport(client = "required", server = "required"), result.env)
    }

    @Test
    fun `parse returns empty metadata for non-mod JAR`() {
        val jarPath = createJar(
            "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0",
        )

        val result = modService.parseModMetadata(jarPath)

        assertNull(result.name)
        assertNull(result.version)
        assertTrue(result.loaders.isEmpty())
        assertNull(result.env)
    }
}
