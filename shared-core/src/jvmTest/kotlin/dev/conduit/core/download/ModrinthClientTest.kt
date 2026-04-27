package dev.conduit.core.download

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.*

class ModrinthClientTest {

    private inline fun <T> withTempDir(block: (Path) -> T): T {
        val dir = Files.createTempDirectory("conduit-modrinth-test")
        try {
            return block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun mockClient(
        requestedUrls: MutableList<String>? = null,
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine { request ->
            requestedUrls?.add(request.url.toString())
            handler(request)
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            expectSuccess = false
        }
    }

    companion object {
        private val searchResponseJson = """
        {
          "hits": [{
            "project_id": "abc123",
            "slug": "my-mod",
            "title": "My Mod",
            "description": "A test mod",
            "author": "author1",
            "icon_url": null,
            "downloads": 42,
            "latest_version": "1.0.0",
            "categories": ["fabric"],
            "client_side": "required",
            "server_side": "optional"
          }],
          "total_hits": 1,
          "offset": 0,
          "limit": 20
        }
        """.trimIndent()

        private val versionJson = """
        {
          "id": "ver1",
          "project_id": "abc123",
          "version_number": "1.0.0",
          "name": "Version 1",
          "changelog": "Initial release",
          "game_versions": ["1.20.4"],
          "loaders": ["fabric"],
          "date_published": "2024-01-01T00:00:00Z",
          "files": [{
            "filename": "mod.jar",
            "url": "https://cdn.modrinth.com/mod.jar",
            "size": 12345,
            "hashes": {"sha1": "a1b2", "sha512": "c3d4"}
          }],
          "dependencies": [{"project_id": "dep1", "dependency_type": "required"}]
        }
        """.trimIndent()
    }

    private fun MockRequestHandleScope.jsonResponse(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `search returns parsed results with correct field mapping`() = runTest {
        val http = mockClient { jsonResponse(searchResponseJson) }
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
        val http = mockClient(urls) { jsonResponse(searchResponseJson) }
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
    fun `search coerces limit to 1-100 range`() = runTest {
        val urls = mutableListOf<String>()
        val http = mockClient(urls) { jsonResponse(searchResponseJson) }
        val client = ModrinthClient(http)

        client.search("test", limit = 200)
        assertEquals("100", Url(urls[0]).parameters["limit"])

        client.search("test", limit = 0)
        assertEquals("1", Url(urls[1]).parameters["limit"])
    }

    @Test
    fun `getProjectVersions returns version list`() = runTest {
        val http = mockClient { jsonResponse("[$versionJson]") }
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
        val http = mockClient { jsonResponse(versionJson) }
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
        val http = mockClient { respond(content.toByteArray(), headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")) }
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
        val http = mockClient { respond("Not found", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/plain")) }
        val client = ModrinthClient(http)

        val ex = assertFailsWith<ModrinthApiException> {
            client.search("nonexistent")
        }
        assertEquals(404, ex.statusCode)
        assertEquals("Not found", ex.body)
    }
}
