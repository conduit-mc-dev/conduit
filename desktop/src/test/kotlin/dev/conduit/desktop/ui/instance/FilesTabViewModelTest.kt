package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.DirectoryListing
import dev.conduit.desktop.*
import dev.conduit.desktop.ui.components.ToastManager
import dev.conduit.desktop.ui.components.ToastType
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FilesTabViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
    }

    private val emptyListing = DirectoryListing(path = "", entries = emptyList())

    @Test
    fun `uploadFile writes bytes and shows success toast`() = runBlocking {
        var writePath: String? = null
        var writeBody: ByteArray? = null
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst/files" -> respond(
                    mockJsonBody(emptyListing),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/files/content" -> {
                    writePath = request.url.parameters["path"]
                    writeBody = request.body.toByteArray()
                    respond(
                        """{"path":"${writePath}","size":${writeBody?.size ?: 0},"lastModified":"2026-05-10T00:00:00Z"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val toastManager = ToastManager()
        val vm = FilesTabViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client), toastManager)
        val testBytes = "hello world".encodeToByteArray()

        vm.uploadFile("test.txt", testBytes)

        // Wait for the coroutine to complete by observing the toast
        val toasts = toastManager.toasts.first { it.isNotEmpty() }
        assertEquals(ToastType.Success, toasts.first().type)
        assertTrue(toasts.first().text.contains("test.txt"))
        assertEquals("test.txt", writePath)
        assertContentEquals(testBytes, writeBody)
    }

    @Test
    fun `createFolder writes keep file and shows success toast`() = runBlocking {
        var writePath: String? = null
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst/files" -> respond(
                    mockJsonBody(emptyListing),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/files/content" -> {
                    writePath = request.url.parameters["path"]
                    respond(
                        """{"path":"${writePath}","size":0,"lastModified":"2026-05-10T00:00:00Z"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val toastManager = ToastManager()
        val vm = FilesTabViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client), toastManager)

        vm.createFolder("config")

        // Wait for the coroutine to complete by observing the toast
        val toasts = toastManager.toasts.first { it.isNotEmpty() }
        assertEquals(ToastType.Success, toasts.first().type)
        assertTrue(toasts.first().text.contains("config"))
        assertEquals("config/.keep", writePath)
    }

    @Test
    fun `uploadFile shows error toast on write failure`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst/files" -> respond(
                    mockJsonBody(emptyListing),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/files/content" ->
                    respondError(HttpStatusCode.UnprocessableEntity)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val toastManager = ToastManager()
        val vm = FilesTabViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client), toastManager)

        vm.uploadFile("bad.txt", byteArrayOf())

        val toasts = toastManager.toasts.first { it.isNotEmpty() }
        assertEquals(ToastType.Error, toasts.first().type)
    }

    @Test
    fun `createFolder shows error toast on write failure`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst/files" -> respond(
                    mockJsonBody(emptyListing),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/files/content" ->
                    respondError(HttpStatusCode.UnprocessableEntity)
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val toastManager = ToastManager()
        val vm = FilesTabViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client), toastManager)

        vm.createFolder("bad")

        val toasts = toastManager.toasts.first { it.isNotEmpty() }
        assertEquals(ToastType.Error, toasts.first().type)
    }
}
