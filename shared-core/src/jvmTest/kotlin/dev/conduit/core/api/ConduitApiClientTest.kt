@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package dev.conduit.core.api

import dev.conduit.core.model.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class ConduitApiClientTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun createClient(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ConduitApiClient {
        val mockEngine = MockEngine { request -> handler(request) }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(this@ConduitApiClientTest.json) }
            expectSuccess = false
        }
        return ConduitApiClient(
            baseUrl = "http://test",
            token = "test-token",
            httpClient = httpClient,
        )
    }

    // --- cancelTask tests ---

    @Test
    fun `cancelTask returns cancelled true on success`() = runTest {
        val client = createClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.encodedPath.endsWith("/cancel"))
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(
                """{"cancelled":true}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.cancelTask("task-1")
        assertTrue(result.cancelled)
    }

    @Test
    fun `cancelTask throws on 404 TASK_NOT_FOUND`() = runTest {
        val client = createClient { _ ->
            respond(
                """{"error":{"code":"TASK_NOT_FOUND","message":"Task not found"}}""",
                HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertFailsWith<ConduitApiException> { client.cancelTask("task-1") }
        assertEquals(404, ex.httpStatus)
        assertEquals("TASK_NOT_FOUND", ex.errorResponse?.error?.code)
    }

    @Test
    fun `cancelTask throws on 409 TASK_NOT_CANCELLABLE`() = runTest {
        val client = createClient { _ ->
            respond(
                """{"error":{"code":"TASK_NOT_CANCELLABLE","message":"Task already completed"}}""",
                HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertFailsWith<ConduitApiException> { client.cancelTask("task-2") }
        assertEquals(409, ex.httpStatus)
        assertEquals("TASK_NOT_CANCELLABLE", ex.errorResponse?.error?.code)
    }

    // --- writeFile tests ---

    @Test
    fun `writeFile returns FileWriteResponse on success`() = runTest {
        val content = "key=value\n".encodeToByteArray()
        val client = createClient { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertTrue(request.url.encodedPath.contains("/files/content"))
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(
                """{"path":"config/test.properties","size":10,"lastModified":"2026-05-10T12:00:00Z"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.writeFile("inst-1", "config/test.properties", content)
        assertEquals("config/test.properties", result.path)
        assertEquals(10L, result.size)
        assertEquals("2026-05-10T12:00:00Z", result.lastModified)
    }

    @Test
    fun `writeFile throws on 422 FILE_PROTECTED`() = runTest {
        val content = "bad".encodeToByteArray()
        val client = createClient { _ ->
            respond(
                """{"error":{"code":"FILE_PROTECTED","message":"File is protected"}}""",
                HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val ex = assertFailsWith<ConduitApiException> { client.writeFile("inst-1", "server.jar", content) }
        assertEquals(422, ex.httpStatus)
        assertEquals("FILE_PROTECTED", ex.errorResponse?.error?.code)
    }

    @Test
    fun `writeFile sends correct path as query parameter`() = runTest {
        val content = "binary-data".encodeToByteArray()
        val client = createClient { request ->
            assertTrue(request.url.encodedPath.contains("/files/content"))
            assertTrue(
                request.url.encodedQuery.contains("path=mods%2Ftest.jar") ||
                    request.url.parameters["path"] == "mods/test.jar",
            )
            respond(
                """{"path":"mods/test.jar","size":${content.size},"lastModified":"2026-05-10T12:00:00Z"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = client.writeFile("inst-1", "mods/test.jar", content)
        assertEquals("mods/test.jar", result.path)
    }
}
