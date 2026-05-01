package dev.conduit.core.download

import dev.conduit.core.model.DownloadSource
import dev.conduit.core.testutil.jsonResponse
import dev.conduit.core.testutil.loadFixture
import dev.conduit.core.testutil.mockHttpClient
import dev.conduit.core.testutil.withTempDir
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MojangClientTest {

    private val jarContent = "fake-server-jar-content".toByteArray()
    private val jarSha1 = "0a08e2d523081e88ffef01e923c6f2de074108e7"

    private val manifestJson = loadFixture("mojang/version_manifest_1.20.4.json")

    private val versionDetailJson = loadFixture(
        "mojang/version_detail_1.20.4.json",
        mapOf("SHA1" to jarSha1, "SIZE" to jarContent.size.toString()),
    )

    private fun MockRequestHandleScope.routeStandard(request: io.ktor.client.request.HttpRequestData) =
        when {
            request.url.encodedPath.contains("version_manifest") -> jsonResponse(manifestJson)
            request.url.encodedPath.contains("1.20.4.json") -> jsonResponse(versionDetailJson)
            request.url.encodedPath.contains("server.jar") -> respond(jarContent, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            else -> respondError(HttpStatusCode.NotFound)
        }

    @Test
    fun `fetchManifest with MOJANG requests original url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockHttpClient(requestedUrls = urls) { jsonResponse(manifestJson) }

        MojangClient(downloadSourceProvider = { DownloadSource.MOJANG to null }, httpClient = client)
            .fetchManifest()

        assertTrue(urls[0].contains("launchermeta.mojang.com"))
    }

    @Test
    fun `fetchManifest with BMCLAPI requests bmclapi url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockHttpClient(requestedUrls = urls) { jsonResponse(manifestJson) }

        MojangClient(downloadSourceProvider = { DownloadSource.BMCLAPI to null }, httpClient = client)
            .fetchManifest(forceRefresh = true)

        assertTrue(urls[0].contains("bmclapi2.bangbang93.com"))
    }

    @Test
    fun `fetchManifest with CUSTOM uses custom url`() = runTest {
        val urls = mutableListOf<String>()
        val client = mockHttpClient(requestedUrls = urls) { jsonResponse(manifestJson) }

        MojangClient(downloadSourceProvider = { DownloadSource.CUSTOM to "https://my-mirror.example.com" }, httpClient = client)
            .fetchManifest(forceRefresh = true)

        assertTrue(urls[0].contains("my-mirror.example.com"))
    }

    @Test
    fun `downloadServerJar retries on 500`() = runTest {
        var jarCallCount = 0
        val client = mockHttpClient { request ->
            val path = request.url.encodedPath
            when {
                path.contains("version_manifest") -> jsonResponse(manifestJson)
                path.contains("1.20.4.json") -> jsonResponse(versionDetailJson)
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
    fun `downloadServerJar retries on 503`() = runTest {
        var jarCallCount = 0
        val client = mockHttpClient { request ->
            val path = request.url.encodedPath
            when {
                path.contains("version_manifest") -> jsonResponse(manifestJson)
                path.contains("1.20.4.json") -> jsonResponse(versionDetailJson)
                path.contains("server.jar") -> {
                    jarCallCount++
                    if (jarCallCount < 3) respondError(HttpStatusCode.ServiceUnavailable)
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
        val client = mockHttpClient { request ->
            val path = request.url.encodedPath
            when {
                path.contains("version_manifest") -> jsonResponse(manifestJson)
                path.contains("1.20.4.json") -> jsonResponse(versionDetailJson)
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
        val client = mockHttpClient { request -> routeStandard(request) }

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

        val client = mockHttpClient(requestedUrls = urls) { jsonResponse(manifestJson) }

        val mojang = MojangClient(downloadSourceProvider = { currentSource to null }, httpClient = client)

        mojang.fetchManifest(forceRefresh = true)
        assertTrue(urls[0].contains("launchermeta.mojang.com"))

        currentSource = DownloadSource.BMCLAPI
        mojang.fetchManifest(forceRefresh = true)
        assertTrue(urls[1].contains("bmclapi2.bangbang93.com"))
    }
}
