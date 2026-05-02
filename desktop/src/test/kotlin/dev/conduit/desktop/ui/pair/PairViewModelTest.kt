package dev.conduit.desktop.ui.pair

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for PairViewModel.
 *
 * Note: Happy-path tests involving successful API responses inside viewModelScope
 * are deferred due to a known limitation: Ktor 3.x MockEngine response body
 * deserialization fails on Dispatchers.Default, which viewModelScope uses.
 * Error-path tests work because the exception path bypasses body deserialization.
 * Success paths are covered by daemon integration tests (ConduitApiClientTest).
 */
class PairViewModelTest {

    @Test
    fun `connect failure sets error message`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/public/health" -> respond(
                    mockErrorBody("UNAUTHORIZED", "Invalid token"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    status = HttpStatusCode.Unauthorized,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = PairViewModel(client, mockSession(client))
        vm.updateDaemonUrl("http://mock.local")
        vm.connect()
        waitFor { !vm.state.value.isLoading }

        assertEquals(PairStep.CONNECT, vm.state.value.step)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("连接失败"))
    }

    @Test
    fun `confirmPairing with blank code shows validation error`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        val client = mockApiClient(httpClient)
        val vm = PairViewModel(client, mockSession(client))
        vm.updatePairCode("")
        vm.updateDeviceName("")

        var onSuccessCalled = false
        vm.confirmPairing { onSuccessCalled = true }

        assertFalse(onSuccessCalled)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("不能为空"))
    }
}
