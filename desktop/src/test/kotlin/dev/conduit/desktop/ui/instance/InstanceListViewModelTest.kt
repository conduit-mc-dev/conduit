package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.*
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.*
import kotlin.time.Instant

class InstanceListViewModelTest {

    private fun sampleInstance(id: String, name: String, state: InstanceState = InstanceState.STOPPED): InstanceSummary =
        InstanceSummary(
            id = id, name = name, state = state,
            mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    private fun listJson(vararg instances: InstanceSummary): String =
        mockJsonBody(instances.toList())

    private suspend fun InstanceListViewModel.awaitLoad(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isLoading) delay(20)
        }
    }

    @Test
    fun `refresh sets error on failure`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> respond(
                    mockErrorBody("UNAUTHORIZED", "Not authorized"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    status = HttpStatusCode.Unauthorized,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(client, mockSession(client))
        vm.awaitLoad()

        assertTrue(vm.state.value.instances.isEmpty())
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("加载实例列表失败"))
    }

    @Test
    fun `INSTANCE_CREATED event triggers refresh`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val inst1 = sampleInstance("a", "Server A")
        val inst2 = sampleInstance("b", "Server B")
        var callCount = 0
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> {
                    callCount++
                    val instances = if (callCount == 1) listOf(inst1) else listOf(inst1, inst2)
                    respond(
                        listJson(*instances.toTypedArray()),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(client, mockSession(client, wsMessages))
        vm.awaitLoad()
        assertEquals(1, vm.state.value.instances.size)
        assertEquals("a", vm.state.value.instances[0].id)

        wsMessages.emit(
            WsMessage(
                type = WsMessage.INSTANCE_CREATED,
                instanceId = "b",
                payload = TestJson.encodeToJsonElement(mapOf("id" to "b", "name" to "Server B")),
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.instances.size == 2 }

        assertEquals(2, vm.state.value.instances.size)
        assertEquals("a", vm.state.value.instances[0].id)
        assertEquals("b", vm.state.value.instances[1].id)
    }

    @Test
    fun `INSTANCE_DELETED event triggers refresh`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val inst1 = sampleInstance("a", "Server A")
        val inst2 = sampleInstance("b", "Server B")
        var callCount = 0
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> {
                    callCount++
                    val instances = if (callCount == 1) listOf(inst1, inst2) else listOf(inst1)
                    respond(
                        listJson(*instances.toTypedArray()),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(client, mockSession(client, wsMessages))
        vm.awaitLoad()
        assertEquals(2, vm.state.value.instances.size)

        wsMessages.emit(
            WsMessage(
                type = WsMessage.INSTANCE_DELETED,
                instanceId = "b",
                payload = TestJson.encodeToJsonElement(mapOf("id" to "b")),
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.instances.size == 1 }

        assertEquals(1, vm.state.value.instances.size)
        assertEquals("a", vm.state.value.instances[0].id)
    }

    @Test
    fun `STATE_CHANGED event updates instance state in list`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val inst1 = sampleInstance("a", "Server A", InstanceState.STOPPED)
        val inst2 = sampleInstance("b", "Server B", InstanceState.STOPPED)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> respond(
                    listJson(inst1, inst2),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(client, mockSession(client, wsMessages))
        vm.awaitLoad()
        assertEquals(InstanceState.STOPPED, vm.state.value.instances[0].state)
        assertEquals(InstanceState.STOPPED, vm.state.value.instances[1].state)

        wsMessages.emit(
            WsMessage(
                type = WsMessage.STATE_CHANGED,
                instanceId = "a",
                payload = TestJson.encodeToJsonElement(
                    StateChangedPayload(oldState = InstanceState.STOPPED, newState = InstanceState.RUNNING)
                ),
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.instances.find { it.id == "a" }?.state == InstanceState.RUNNING }

        assertEquals(InstanceState.RUNNING, vm.state.value.instances.find { it.id == "a" }?.state)
        assertEquals(InstanceState.STOPPED, vm.state.value.instances.find { it.id == "b" }?.state)
    }
}
