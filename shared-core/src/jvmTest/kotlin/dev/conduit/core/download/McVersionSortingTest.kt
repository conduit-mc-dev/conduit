package dev.conduit.core.download

import dev.conduit.core.model.DownloadSource
import dev.conduit.core.model.MinecraftVersion
import dev.conduit.core.testutil.jsonResponse
import dev.conduit.core.testutil.loadFixture
import dev.conduit.core.testutil.mockHttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class McVersionSortingTest {

    // ============================================================
    // compareMcVersions unit tests
    // ============================================================

    @Test
    fun `release versions sort in descending order`() {
        // Given: unsorted release versions
        val versions = listOf("1.20.1", "1.21", "1.8.9", "1.19.4", "1.20.4")
        // When: sorted with compareMcVersions
        val sorted = versions.sortedWith(Comparator { a, b -> compareMcVersions(a, b) })
        // Then: newest first
        assertEquals(
            listOf("1.21", "1.20.4", "1.20.1", "1.19.4", "1.8.9"),
            sorted,
        )
    }

    @Test
    fun `different-length version strings sort correctly`() {
        val versions = listOf("1.8", "1.20", "1.8.1", "1.20.1", "1.8.10")
        val sorted = versions.sortedWith(Comparator { a, b -> compareMcVersions(a, b) })
        assertEquals(
            listOf("1.20.1", "1.20", "1.8.10", "1.8.1", "1.8"),
            sorted,
        )
    }

    @Test
    fun `snapshots sort in descending order`() {
        val versions = listOf("24w40a", "25w14a", "25w10b", "24w35a", "25w14b")
        val sorted = versions.sortedWith(Comparator { a, b -> compareMcVersions(a, b) })
        assertEquals(
            listOf("25w14b", "25w14a", "25w10b", "24w40a", "24w35a"),
            sorted,
        )
    }

    @Test
    fun `pre-releases sort before release version`() {
        val versions = listOf("1.21", "1.21-pre1", "1.21-pre2", "1.21-rc1")
        val sorted = versions.sortedWith(Comparator { a, b -> compareMcVersions(a, b) })
        // rc after pre, release after rc: 1.21 > 1.21-rc1 > 1.21-pre2 > 1.21-pre1
        assertEquals(
            listOf("1.21", "1.21-rc1", "1.21-pre2", "1.21-pre1"),
            sorted,
        )
    }

    @Test
    fun `equal versions compare as zero`() {
        assertEquals(0, compareMcVersions("1.21.5", "1.21.5"))
        assertEquals(0, compareMcVersions("25w14a", "25w14a"))
    }

    @Test
    fun `newer version returns negative`() {
        assertTrue(compareMcVersions("1.21.5", "1.20.4") < 0, "1.21.5 should be newer than 1.20.4")
        assertTrue(compareMcVersions("1.20.4", "1.20.1") < 0)
        assertTrue(compareMcVersions("1.20.1", "1.19.4") < 0)
        assertTrue(compareMcVersions("25w14a", "24w40a") < 0)
    }

    @Test
    fun `older version returns positive`() {
        assertTrue(compareMcVersions("1.20.4", "1.21.5") > 0, "1.20.4 should be older than 1.21.5")
        assertTrue(compareMcVersions("1.8.9", "1.20.1") > 0)
        assertTrue(compareMcVersions("24w40a", "25w14a") > 0)
    }

    @Test
    fun `version with more segments is newer when prefix matches`() {
        assertTrue(compareMcVersions("1.20.1", "1.20") < 0, "1.20.1 newer than 1.20")
        assertTrue(compareMcVersions("1.8.10", "1.8.1") < 0, "1.8.10 newer than 1.8.1")
    }

    @Test
    fun `complex mixed case sorts correctly`() {
        val versions = listOf(
            "1.8", "1.21-pre1", "1.20.4", "1.21.5", "25w14a",
            "1.21", "1.21-rc1", "24w40a", "1.8.9", "1.19.4",
        )
        val sorted = versions.sortedWith(Comparator { a, b -> compareMcVersions(a, b) })
        // All versions by descending version order (no type grouping here — just pure version compare)
        val expected = listOf(
            "1.21.5", "1.21", "1.21-rc1", "1.21-pre1",
            "1.20.4", "1.19.4", "1.8.9", "1.8",
        )
        // Snapshots (25w14a, 24w40a) mixed in — they are compared lexicographically
        // against releases; exact position depends on the comparator semantics
        // We only assert releases are in correct relative order
        val releaseOnly = sorted.filter { !it.startsWith("25w") && !it.startsWith("24w") }
        assertEquals(expected, releaseOnly)
    }

    // ============================================================
    // listVersions() integration tests
    // ============================================================

    private val unsortedManifestJson = loadFixture("mojang/version_manifest_unsorted.json")

    @Test
    fun `listVersions release type returns releases sorted newest first`() = runTest {
        val client = mockHttpClient { jsonResponse(unsortedManifestJson) }
        val mojang = MojangClient(
            downloadSourceProvider = { DownloadSource.MOJANG to null },
            httpClient = client,
        )

        val versions = mojang.listVersions(type = "release", forceRefresh = true)

        assertEquals(5, versions.size)
        assertEquals(
            listOf("1.21.5", "1.20.4", "1.20.1", "1.19.4", "1.8.9"),
            versions.map { it.id },
        )
        assertTrue(versions.all { it.type == "release" })
    }

    @Test
    fun `listVersions snapshot type returns snapshots sorted newest first`() = runTest {
        val client = mockHttpClient { jsonResponse(unsortedManifestJson) }
        val mojang = MojangClient(
            downloadSourceProvider = { DownloadSource.MOJANG to null },
            httpClient = client,
        )

        val versions = mojang.listVersions(type = "snapshot", forceRefresh = true)

        assertEquals(2, versions.size)
        assertEquals(
            listOf("25w14a", "24w40a"),
            versions.map { it.id },
        )
        assertTrue(versions.all { it.type == "snapshot" })
    }

    @Test
    fun `listVersions all type returns releases before snapshots`() = runTest {
        val client = mockHttpClient { jsonResponse(unsortedManifestJson) }
        val mojang = MojangClient(
            downloadSourceProvider = { DownloadSource.MOJANG to null },
            httpClient = client,
        )

        val versions = mojang.listVersions(type = "all", forceRefresh = true)

        assertEquals(7, versions.size)

        // Releases come first
        val releaseVersions = versions.filter { it.type == "release" }
        val snapshotVersions = versions.filter { it.type == "snapshot" }
        assertEquals(5, releaseVersions.size)
        assertEquals(2, snapshotVersions.size)

        // All releases appear before all snapshots
        val lastReleaseIndex = versions.indexOfLast { it.type == "release" }
        val firstSnapshotIndex = versions.indexOfFirst { it.type == "snapshot" }
        assertTrue(lastReleaseIndex < firstSnapshotIndex, "All releases should come before snapshots")

        // Releases sorted newest first
        assertEquals(
            listOf("1.21.5", "1.20.4", "1.20.1", "1.19.4", "1.8.9"),
            releaseVersions.map { it.id },
        )

        // Snapshots sorted newest first
        assertEquals(
            listOf("25w14a", "24w40a"),
            snapshotVersions.map { it.id },
        )
    }

    @Test
    fun `listVersions maps to MinecraftVersion correctly`() = runTest {
        val client = mockHttpClient { jsonResponse(unsortedManifestJson) }
        val mojang = MojangClient(
            downloadSourceProvider = { DownloadSource.MOJANG to null },
            httpClient = client,
        )

        val versions = mojang.listVersions(type = "release", forceRefresh = true)

        val top = versions.first()
        assertIs<MinecraftVersion>(top)
        assertEquals("1.21.5", top.id)
        assertEquals("release", top.type)
        assertTrue(top.releaseTime.isNotEmpty())
    }

    @Test
    fun `listVersions with empty manifest returns empty list`() = runTest {
        val emptyManifest = """{"latest":{"release":"","snapshot":""},"versions":[]}"""
        val client = mockHttpClient { jsonResponse(emptyManifest) }
        val mojang = MojangClient(
            downloadSourceProvider = { DownloadSource.MOJANG to null },
            httpClient = client,
        )

        val versions = mojang.listVersions(type = "all", forceRefresh = true)

        assertTrue(versions.isEmpty())
    }
}
