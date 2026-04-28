package dev.conduit.core.download

import dev.conduit.core.testutil.loadFixture
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MojangManifestParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse version manifest`() {
        val fixture = loadFixture("mojang/version_manifest_multi.json")

        val manifest = json.decodeFromString<MojangVersionManifest>(fixture)

        assertEquals("1.21.5", manifest.latest.release)
        assertEquals("25w14a", manifest.latest.snapshot)
        assertEquals(3, manifest.versions.size)

        val release = manifest.versions.first { it.id == "1.21.5" }
        assertEquals("release", release.type)
        assertTrue(release.url.contains("1.21.5.json"))

        val releases = manifest.versions.filter { it.type == "release" }
        assertEquals(2, releases.size)
    }

    @Test
    fun `parse version detail with server download`() {
        val fixture = loadFixture("mojang/version_detail_full.json")

        val detail = json.decodeFromString<MojangVersionDetail>(fixture)

        assertEquals("1.21.5", detail.id)
        val server = assertNotNull(detail.downloads.server)
        assertEquals("bbb222", server.sha1)
        assertEquals(50000000L, server.size)
        assertTrue(server.url.endsWith("server.jar"))
        assertNotNull(detail.downloads.client)
    }

    @Test
    fun `parse version detail without server download`() {
        val fixture = loadFixture("mojang/version_detail_no_server.json")

        val detail = json.decodeFromString<MojangVersionDetail>(fixture)
        assertEquals(null, detail.downloads.server)
        assertNotNull(detail.downloads.client)
    }
}
