package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.ServerPropertiesUpdateResponse
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class ServerPropertiesViewModelTest {

    private suspend fun ServerPropertiesViewModel.awaitLoad(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isLoading) delay(20)
        }
    }

    private suspend fun ServerPropertiesViewModel.awaitSave(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isSaving) delay(20)
        }
    }

    @Test
    fun `loadProperties populates state with server properties`() = runBlocking {
        val properties = mapOf(
            "motd" to "My Server",
            "max-players" to "20",
            "difficulty" to "normal",
        )
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(
                        mockJsonBody(properties),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = ServerPropertiesViewModel("i1", client)
        vm.awaitLoad()

        assertEquals(properties, vm.state.value.properties)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `loadProperties sets error on API failure`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.InternalServerError) }
        val client = mockApiClient(httpClient)
        val vm = ServerPropertiesViewModel("i1", client)
        vm.awaitLoad()

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.properties.isEmpty())
    }

    @Test
    fun `updateValue modifies editedValues without touching properties`() = runBlocking {
        val properties = mapOf("motd" to "Original")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(
                        mockJsonBody(properties),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = ServerPropertiesViewModel("i1", client)
        vm.awaitLoad()

        vm.updateValue("motd", "New MOTD")
        assertEquals("New MOTD", vm.state.value.editedValues["motd"])
        assertEquals("Original", vm.state.value.properties["motd"]) // original untouched
    }

    @Test
    fun `save sends changed values and sets saveSuccess`() = runBlocking {
        val properties = mapOf("motd" to "Original", "max-players" to "20")
        var saveCalled = false
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" -> {
                    when (request.method) {
                        HttpMethod.Get -> respond(
                            mockJsonBody(properties),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        HttpMethod.Put -> {
                            saveCalled = true
                            respond(
                                mockJsonBody(ServerPropertiesUpdateResponse(updated = listOf("motd"), restartRequired = false)),
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                        else -> respondError(HttpStatusCode.MethodNotAllowed)
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = ServerPropertiesViewModel("i1", client)
        vm.awaitLoad()

        vm.updateValue("motd", "New MOTD")
        vm.save()
        vm.awaitSave()

        assertTrue(saveCalled, "Expected PUT request to be made")
        assertTrue(vm.state.value.saveSuccess)
        assertFalse(vm.state.value.restartRequired)
    }

    @Test
    fun `save sets error on API failure`() = runBlocking {
        val properties = mapOf("motd" to "Original")
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/i1/config/server-properties" &&
                    request.method == HttpMethod.Get ->
                    respond(
                        mockJsonBody(properties),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                else -> respondError(HttpStatusCode.InternalServerError)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = ServerPropertiesViewModel("i1", client)
        vm.awaitLoad()

        vm.updateValue("motd", "New")
        vm.save()
        vm.awaitSave()

        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.saveSuccess)
    }

    @Test
    fun `dismissSuccess clears saveSuccess flag`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        val vm = ServerPropertiesViewModel("i1", mockApiClient(httpClient))
        vm.dismissSuccess()
        assertFalse(vm.state.value.saveSuccess)
    }
}
