package dev.conduit.core.download

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.download.MojangClient
import dev.conduit.core.testutil.jsonResponse
import dev.conduit.core.testutil.loadFixture
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class HttpHeaderAssertionTest {

    // ---------- helper ----------

    /**
     * Creates a MockEngine-based HttpClient that captures the User-Agent header
     * seen on each request, and includes [defaultRequest] logic similar to the
     * production CIO clients so the test exercises the same code path.
     */
    private fun mockWithUserAgentCapture(
        captureUserAgent: MutableList<String?>,
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient = HttpClient(MockEngine { request ->
        captureUserAgent.add(request.headers["User-Agent"])
        handler(request)
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        defaultRequest {
            header("User-Agent", "Conduit-MC/0.1.0 (https://github.com/conduit-mc-dev/conduit)")
        }
        expectSuccess = false
    }

    // ---------- MojangClient ----------

    @Test
    fun `MojangClient sends User-Agent header`() = runTest {
        val userAgents = mutableListOf<String?>()
        val manifestJson = loadFixture("mojang/version_manifest_1.20.4.json")

        val client = mockWithUserAgentCapture(userAgents) { jsonResponse(manifestJson) }

        MojangClient(httpClient = client).fetchManifest(forceRefresh = true)

        assertEquals(1, userAgents.size, "Expected exactly one request")
        val userAgent = userAgents[0]
        assertNotNull(userAgent, "User-Agent header must be present")
        assertTrue(userAgent!!.isNotEmpty(), "User-Agent header must not be empty")
    }

    // ---------- ModrinthClient ----------

    @Test
    fun `ModrinthClient sends User-Agent header`() = runTest {
        val userAgents = mutableListOf<String?>()
        val versionJson = loadFixture("modrinth/version_ver1.json")

        val client = mockWithUserAgentCapture(userAgents) { jsonResponse(versionJson) }

        ModrinthClient(client).getVersion("ver1")

        assertEquals(1, userAgents.size, "Expected exactly one request")
        val userAgent = userAgents[0]
        assertNotNull(userAgent, "User-Agent header must be present")
        assertTrue(userAgent!!.isNotEmpty(), "User-Agent header must not be empty")
    }
}
