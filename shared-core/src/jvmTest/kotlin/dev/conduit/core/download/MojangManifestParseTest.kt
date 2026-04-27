package dev.conduit.core.download

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MojangManifestParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse version manifest`() {
        val fixture = """
        {
          "latest": { "release": "1.21.5", "snapshot": "25w14a" },
          "versions": [
            {
              "id": "1.21.5",
              "type": "release",
              "url": "https://piston-meta.mojang.com/v1/packages/abc/1.21.5.json",
              "time": "2025-03-25T10:00:00+00:00",
              "releaseTime": "2025-03-25T10:00:00+00:00",
              "sha1": "abc123",
              "complianceLevel": 1
            },
            {
              "id": "25w14a",
              "type": "snapshot",
              "url": "https://piston-meta.mojang.com/v1/packages/def/25w14a.json",
              "time": "2025-04-01T10:00:00+00:00",
              "releaseTime": "2025-04-01T10:00:00+00:00",
              "sha1": "def456",
              "complianceLevel": 1
            },
            {
              "id": "1.21.4",
              "type": "release",
              "url": "https://piston-meta.mojang.com/v1/packages/ghi/1.21.4.json",
              "time": "2024-12-03T10:00:00+00:00",
              "releaseTime": "2024-12-03T10:00:00+00:00",
              "sha1": "ghi789",
              "complianceLevel": 1
            }
          ]
        }
        """.trimIndent()

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
        val fixture = """
        {
          "id": "1.21.5",
          "type": "release",
          "downloads": {
            "client": {
              "sha1": "aaa111",
              "size": 25000000,
              "url": "https://piston-data.mojang.com/v1/objects/aaa/client.jar"
            },
            "server": {
              "sha1": "bbb222",
              "size": 50000000,
              "url": "https://piston-data.mojang.com/v1/objects/bbb/server.jar"
            }
          },
          "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
          "mainClass": "net.minecraft.client.main.Main"
        }
        """.trimIndent()

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
        val fixture = """
        {
          "id": "rd-132211",
          "type": "old_alpha",
          "downloads": {
            "client": {
              "sha1": "ccc333",
              "size": 1000000,
              "url": "https://piston-data.mojang.com/v1/objects/ccc/client.jar"
            }
          }
        }
        """.trimIndent()

        val detail = json.decodeFromString<MojangVersionDetail>(fixture)
        assertEquals(null, detail.downloads.server)
        assertNotNull(detail.downloads.client)
    }
}
