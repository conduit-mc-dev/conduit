package dev.conduit.core.download

import dev.conduit.core.model.DownloadSource
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class MojangClientTest {

    private inline fun <T> withTempDir(block: (Path) -> T): T {
        val dir = Files.createTempDirectory("conduit-test")
        try {
            return block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private val jarContent = "fake-server-jar-content".toByteArray()
    private val jarSha1 = "0a08e2d523081e88ffef01e923c6f2de074108e7"

    private val manifestJson = """
    {
      "latest": {"release":"1.20.4","snapshot":"1.20.4"},
      "versions": [{
        "id": "1.20.4",
        "type": "release",
        "url": "https://piston-meta.mojang.com/v1/packages/abc/1.20.4.json",
        "releaseTime": "2023-12-07T00:00:00Z"
      }]
    }
    """.trimIndent()

    private val versionDetailJson = """
    {
      "id": "1.20.4",
      "type": "release",
      "downloads": {
        "server": {
          "sha1": "$jarSha1",
          "size": ${jarContent.size},
          "url": "https://piston-data.mojang.com/v1/objects/abc/server.jar"
        }
      }
    }
    """.trimIndent()

    private fun mockClient(requestedUrls: MutableList<String>? = null, handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request ->
            requestedUrls?.add(request.url.toString())
            handler(request)
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = true
        }
    }

    private fun MockRequestHandleScope.routeStandard(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path.contains("version_manifest") -> respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("1.20.4.json") -> respond(versionDetailJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("server.jar") -> respond(jarContent, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `fetchManifest with MOJANG requests original url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockClient(urls) { respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json")) }

        MojangClient(downloadSourceProvider = { DownloadSource.MOJANG to null }, httpClient = client)
            .fetchManifest()

        assertTrue(urls[0].contains("launchermeta.mojang.com"))
    }

    @Test
    fun `fetchManifest with BMCLAPI requests bmclapi url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockClient(urls) { respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json")) }

        MojangClient(downloadSourceProvider = { DownloadSource.BMCLAPI to null }, httpClient = client)
            .fetchManifest(forceRefresh = true)

        assertTrue(urls[0].contains("bmclapi2.bangbang93.com"))
    }

    @Test
    fun `fetchManifest with CUSTOM uses custom url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockClient(urls) { respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json")) }

        MojangClient(downloadSourceProvider = { DownloadSource.CUSTOM to "https://my-mirror.example.com" }, httpClient = client)
            .fetchManifest(forceRefresh = true)

        assertTrue(urls[0].contains("my-mirror.example.com"))
    }

    @Test
    fun `downloadServerJar retries on 500`() = runTest {
        var jarCallCount = 0
        val client = mockClient { request ->
            val path = request.url.encodedPath
            when {
                path.contains("version_manifest") -> respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("1.20.4.json") -> respond(versionDetailJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("server.jar") -> {
                    jarCallCount++
                    if (jarCallCount < 3) respondError(HttpStatusCode.InternalServerError)
                    else respond(jarContent, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        withTempDir { tempDir ->
            val bytes = MojangClient(httpClient = client).downloadServerJar("1.20.4", tempDir.resolve("server.jar"))
            assertEquals(jarContent.size.toLong(), bytes)
            assertEquals(3, jarCallCount)
        }
    }

    @Test
    fun `downloadServerJar does not retry on 404`() = runTest {
        var jarCallCount = 0
        val client = mockClient { request ->
            val path = request.url.encodedPath
            when {
                path.contains("version_manifest") -> respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("1.20.4.json") -> respond(versionDetailJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                path.contains("server.jar") -> {
                    jarCallCount++
                    respondError(HttpStatusCode.NotFound)
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        withTempDir { tempDir ->
            assertFailsWith<Exception> {
                MojangClient(httpClient = client).downloadServerJar("1.20.4", tempDir.resolve("server.jar"))
            }
            assertEquals(1, jarCallCount)
        }
    }

    @Test
    fun `downloadServerJar calls onProgress`() = runTest {
        val client = mockClient { request -> routeStandard(request) }

        withTempDir { tempDir ->
            val progressCalls = mutableListOf<Pair<Long, Long>>()
            MojangClient(httpClient = client).downloadServerJar("1.20.4", tempDir.resolve("server.jar")) { bytesWritten, totalBytes ->
                progressCalls.add(bytesWritten to totalBytes)
            }

            assertTrue(progressCalls.isNotEmpty())
            assertEquals(jarContent.size.toLong(), progressCalls.last().first)
            assertEquals(jarContent.size.toLong(), progressCalls.last().second)
        }
    }

    @Test
    fun `mirror url reflects config change`() = runTest {
        val urls = mutableListOf<String>()
        var currentSource = DownloadSource.MOJANG

        val client = mockClient(urls) { respond(manifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json")) }

        val mojang = MojangClient(downloadSourceProvider = { currentSource to null }, httpClient = client)

        mojang.fetchManifest(forceRefresh = true)
        assertTrue(urls[0].contains("launchermeta.mojang.com"))

        currentSource = DownloadSource.BMCLAPI
        mojang.fetchManifest(forceRefresh = true)
        assertTrue(urls[1].contains("bmclapi2.bangbang93.com"))
    }
}
