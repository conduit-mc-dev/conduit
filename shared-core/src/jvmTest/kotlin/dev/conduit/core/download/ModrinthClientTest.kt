package dev.conduit.core.download

import dev.conduit.core.testutil.jsonResponse
import dev.conduit.core.testutil.loadFixture
import dev.conduit.core.testutil.mockHttpClient
import dev.conduit.core.testutil.withTempDir
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.io.path.readText
import kotlin.test.*

class ModrinthClientTest {

    companion object {
        private val searchResponseJson = loadFixture("modrinth/search_response.json")
        private val versionJson = loadFixture("modrinth/version_ver1.json")
    }


    @Test
    fun `search returns parsed results with correct field mapping`() = runTest {
        val http = mockHttpClient(expectSuccess = false) { jsonResponse(searchResponseJson) }
        val client = ModrinthClient(http)

        val result = client.search("test")

        assertEquals(1, result.totalHits)
        assertEquals(1, result.hits.size)
        val hit = result.hits[0]
        assertEquals("abc123", hit.projectId)
        assertEquals("my-mod", hit.slug)
        assertEquals("My Mod", hit.title)
        assertEquals("author1", hit.author)
        assertEquals(42L, hit.downloads)
        assertEquals("1.0.0", hit.latestVersion)
        assertEquals(listOf("fabric"), hit.categories)
        assertEquals("required", hit.env?.client)
        assertEquals("optional", hit.env?.server)
    }

    @Test
    fun `search builds correct facets with mcVersion and loader`() = runTest {
        val urls = mutableListOf<String>()
        val http = mockHttpClient(expectSuccess = false, requestedUrls = urls) { jsonResponse(searchResponseJson) }
        val client = ModrinthClient(http)

        client.search("test", mcVersion = "1.20.4", loader = "fabric")

        val url = urls.first()
        assertTrue(url.contains("facets="), "URL should contain facets parameter")
        val facets = Url(url).parameters["facets"]!!
        assertTrue(facets.contains("""["project_type:mod"]"""))
        assertTrue(facets.contains("""["versions:1.20.4"]"""))
        assertTrue(facets.contains("""["categories:fabric"]"""))
    }

    @Test
    fun `search builds facets with client and server side filters`() = runTest {
        val urls = mutableListOf<String>()
        val http = mockHttpClient(expectSuccess = false, requestedUrls = urls) { jsonResponse(searchResponseJson) }
        val client = ModrinthClient(http)

        client.search("test", clientSide = "required", serverSide = "unsupported")

        val url = urls.first()
        val facets = Url(url).parameters["facets"]!!
        assertTrue(facets.contains("""["client_side:required"]"""))
        assertTrue(facets.contains("""["server_side:unsupported"]"""))
    }

    @Test
    fun `search coerces limit to 1-100 range`() = runTest {
        val urls = mutableListOf<String>()
        val http = mockHttpClient(expectSuccess = false, requestedUrls = urls) { jsonResponse(searchResponseJson) }
        val client = ModrinthClient(http)

        client.search("test", limit = 200)
        assertEquals("100", Url(urls[0]).parameters["limit"])

        client.search("test", limit = 0)
        assertEquals("1", Url(urls[1]).parameters["limit"])
    }

    @Test
    fun `getProjectVersions returns version list`() = runTest {
        val http = mockHttpClient(expectSuccess = false) { jsonResponse("[$versionJson]") }
        val client = ModrinthClient(http)

        val result = client.getProjectVersions("abc123")

        assertEquals(1, result.size)
        val v = result[0]
        assertEquals("ver1", v.versionId)
        assertEquals("abc123", v.projectId)
        assertEquals("1.0.0", v.versionNumber)
        assertEquals(listOf("1.20.4"), v.gameVersions)
        assertEquals(listOf("fabric"), v.loaders)
        assertEquals(1, v.dependencies.size)
        assertEquals("dep1", v.dependencies[0].projectId)
        assertEquals("required", v.dependencies[0].dependencyType)
    }

    @Test
    fun `getVersion returns single version with file hashes`() = runTest {
        val http = mockHttpClient(expectSuccess = false) { jsonResponse(versionJson) }
        val client = ModrinthClient(http)

        val result = client.getVersion("ver1")

        assertEquals("ver1", result.versionId)
        assertEquals("Version 1", result.name)
        assertEquals("Initial release", result.changelog)
        assertEquals(1, result.files.size)
        val file = result.files[0]
        assertEquals("mod.jar", file.fileName)
        assertEquals(12345L, file.fileSize)
        assertEquals("a1b2", file.hashes?.sha1)
        assertEquals("c3d4", file.hashes?.sha512)
    }

    @Test
    fun `downloadFile writes content to disk`() = runTest {
        val content = "hello modrinth"
        val http = mockHttpClient(expectSuccess = false) { respond(content.toByteArray(), headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")) }
        val client = ModrinthClient(http)

        withTempDir { dir ->
            val dest = dir.resolve("test.jar")
            val bytesWritten = client.downloadFile("https://cdn.modrinth.com/test.jar", dest)

            assertEquals(content.length.toLong(), bytesWritten)
            assertEquals(content, dest.readText())
        }
    }

    @Test
    fun `API error throws ModrinthApiException`() = runTest {
        val http = mockHttpClient(expectSuccess = false) { respond("Not found", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/plain")) }
        val client = ModrinthClient(http)

        val ex = assertFailsWith<ModrinthApiException> {
            client.search("nonexistent")
        }
        assertEquals(404, ex.statusCode)
        assertEquals("Not found", ex.body)
    }

    @Test
    fun `batchCheckUpdates returns latest versions for given hashes`() = runTest {
        val batchResponse = """{"sha512-hash-a":$versionJson,"sha512-hash-b":null}"""
        val http = mockHttpClient(expectSuccess = false) { jsonResponse(batchResponse) }
        val client = ModrinthClient(http)

        val result = client.batchCheckUpdates(
            hashes = listOf("sha512-hash-a", "sha512-hash-b"),
            algorithm = "sha512",
            loaders = listOf("fabric"),
            gameVersions = listOf("1.20.4"),
        )

        assertEquals(2, result.size)
        assertNotNull(result["sha512-hash-a"], "hash-a should have an update")
        assertEquals("ver1", result["sha512-hash-a"]!!.versionId)
        assertNull(result["sha512-hash-b"], "hash-b should have no update")
    }

    @Test
    fun `batchCheckUpdates returns empty map for unknown hash`() = runTest {
        val http = mockHttpClient(expectSuccess = false) { jsonResponse("""{}""") }
        val client = ModrinthClient(http)

        val result = client.batchCheckUpdates(
            hashes = listOf("unknown-hash"),
            algorithm = "sha512",
            loaders = listOf("fabric"),
            gameVersions = listOf("1.20.4"),
        )

        assertTrue(result.isEmpty())
    }
}
