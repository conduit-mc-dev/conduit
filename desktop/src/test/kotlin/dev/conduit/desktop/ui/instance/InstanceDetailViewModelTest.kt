package dev.conduit.desktop.ui.instance

import dev.conduit.core.api.ConduitWsClient
import dev.conduit.core.model.*
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Instant

class InstanceDetailViewModelTest {

    private fun mockWsClient(): ConduitWsClient {
        val flow = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val wsClient = mockk<ConduitWsClient>(relaxed = true)
        every { wsClient.connect(any()) } answers {}
        coEvery { wsClient.subscribe(any(), any()) } coAnswers { }
        every { wsClient.messages } returns flow
        every { wsClient.close() } answers {}
        return wsClient
    }

    private suspend fun InstanceDetailViewModel.awaitLoad(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isLoading) delay(20)
        }
    }

    private suspend fun InstanceDetailViewModel.awaitAction(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isActionInProgress) delay(20)
        }
    }

    @Test
    fun `startServer with eula not accepted shows dialog`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.STOPPED,
                            mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
                            createdAt = Instant.fromEpochMilliseconds(0),
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/server/eula" -> respond(
                    mockJsonBody(EulaResponse(accepted = false)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", client, mockWsClient())
        vm.awaitLoad()

        vm.startServer()
        waitFor { vm.state.value.showEulaDialog }

        assertTrue(vm.state.value.showEulaDialog)
    }

    @Test
    fun `deleteInstance success sets isDeleted`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            val path = request.url.encodedPath
            if (request.method == HttpMethod.Delete && path == "/api/v1/instances/test-inst") {
                respond(content = "", status = HttpStatusCode.NoContent)
            } else {
                when (path) {
                    "/api/v1/instances/test-inst" -> respond(
                        mockJsonBody(
                            InstanceSummary(
                                id = "test-inst", name = "My Server", state = InstanceState.STOPPED,
                                mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
                                createdAt = Instant.fromEpochMilliseconds(0),
                            )
                        ),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "/api/v1/instances/test-inst/server/eula" -> respond(
                        mockJsonBody(EulaResponse(accepted = false)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", client, mockWsClient())
        vm.awaitLoad()

        vm.deleteInstance()
        waitFor { vm.state.value.isDeleted || vm.state.value.error != null }

        assertTrue(vm.state.value.isDeleted)
    }

    @Test
    fun `deleteInstance failure sets error`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            val path = request.url.encodedPath
            if (request.method == HttpMethod.Delete && path == "/api/v1/instances/test-inst") {
                respond(
                    content = mockErrorBody("INSTANCE_RUNNING", "Instance must be stopped before deletion"),
                    status = HttpStatusCode.Conflict,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                when (path) {
                    "/api/v1/instances/test-inst" -> respond(
                        mockJsonBody(
                            InstanceSummary(
                                id = "test-inst", name = "My Server", state = InstanceState.RUNNING,
                                mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
                                createdAt = Instant.fromEpochMilliseconds(0),
                            )
                        ),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    "/api/v1/instances/test-inst/server/eula" -> respond(
                        mockJsonBody(EulaResponse(accepted = true)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    else -> respondError(HttpStatusCode.NotFound)
                }
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", client, mockWsClient())
        vm.awaitLoad()

        vm.deleteInstance()
        waitFor { vm.state.value.error != null || vm.state.value.isDeleted }

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Instance must be stopped before deletion"))
        assertFalse(vm.state.value.isDeleted)
    }

}
