package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.ServerPropertiesUpdateResponse
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class ConfigTabViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
    }

    private suspend fun ConfigTabViewModel.awaitLoad(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isLoading) delay(20)
        }
    }

    @Test
    fun `loadProperties populates state with server properties`() = runBlocking {
        val properties = mapOf("motd" to "My Server", "max-players" to "20", "difficulty" to "normal")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(mockJsonBody(properties), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val vm = ConfigTabViewModel("i1", TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))
        vm.awaitLoad()

        assertEquals(3, vm.state.value.properties.size)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `loadProperties sets error on API failure`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.InternalServerError) }
        val vm = ConfigTabViewModel("i1", TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))
        vm.awaitLoad()

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.properties.isEmpty())
    }

    @Test
    fun `updateProperty marks property as modified`() = runBlocking {
        val properties = mapOf("motd" to "Original")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(mockJsonBody(properties), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val vm = ConfigTabViewModel("i1", TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))
        vm.awaitLoad()

        vm.updateProperty("motd", "New MOTD")
        val prop = vm.state.value.properties.find { it.key == "motd" }
        assertNotNull(prop)
        assertEquals("New MOTD", prop.currentValue)
        assertEquals("Original", prop.originalValue)
        assertTrue(prop.isModified)
    }

    @Test
    fun `save sends changed values and reloads`() = runBlocking {
        val properties = mapOf("motd" to "Original", "max-players" to "20")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(
                        mockJsonBody(properties),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Put ->
                    respond(
                        mockJsonBody(ServerPropertiesUpdateResponse(updated = listOf("motd"), restartRequired = false)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val vm = ConfigTabViewModel("i1", TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))
        vm.awaitLoad()

        vm.updateProperty("motd", "New MOTD")
        val done = CompletableDeferred<Unit>()
        vm.save { done.complete(Unit) }
        done.await()

        assertFalse(vm.state.value.isSaving)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.properties.any { it.isModified })
    }

    @Test
    fun `revertAll resets all properties to original values`() = runBlocking {
        val properties = mapOf("motd" to "Original", "difficulty" to "normal")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(mockJsonBody(properties), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val vm = ConfigTabViewModel("i1", TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))
        vm.awaitLoad()

        vm.updateProperty("motd", "Changed")
        vm.updateProperty("difficulty", "hard")
        assertEquals(2, vm.state.value.modifiedCount)

        vm.revertAll()
        assertEquals(0, vm.state.value.modifiedCount)
        assertTrue(vm.state.value.properties.all { !it.isModified })
    }
}
