package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.*
import dev.conduit.desktop.*
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.session.DaemonSession
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.*
import kotlin.time.Instant
import kotlin.io.path.createTempDirectory

class InstanceListViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
    }

    private fun sampleInstance(id: String, name: String, state: InstanceState = InstanceState.STOPPED): InstanceSummary =
        InstanceSummary(
            id = id, name = name, state = state,
            mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    private fun listJson(vararg instances: InstanceSummary): String =
        mockJsonBody(instances.toList())

    private fun InstanceListUiState.allInstances() = daemonGroups.flatMap { it.instances }

    /**
     * Collects vm.state in background (triggering WhileSubscribed), waits for [condition],
     * returns the matching state snapshot.
     */
    private suspend fun InstanceListViewModel.awaitState(
        timeoutMs: Long = 3000,
        condition: (InstanceListUiState) -> Boolean,
    ): InstanceListUiState = state.awaitState(timeoutMs, condition)

    @Test
    fun `refresh returns empty list on API failure`() = runBlocking {
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
        val vm = InstanceListViewModel(mockDaemonManager(client))
        val s = vm.awaitState { !it.isLoading }
        assertTrue(s.allInstances().isEmpty())
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
                    respond(listJson(*instances.toTypedArray()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(mockDaemonManager(client, wsMessages))
        vm.awaitState { !it.isLoading && it.allInstances().size == 1 }
        assertEquals("a", vm.state.value.allInstances()[0].id)

        wsMessages.emit(WsMessage(
            type = WsMessage.INSTANCE_CREATED, instanceId = "b",
            payload = TestJson.encodeToJsonElement(mapOf("id" to "b", "name" to "Server B")),
            timestamp = Instant.fromEpochMilliseconds(0),
        ))
        vm.awaitState { it.allInstances().size == 2 }
        assertEquals("a", vm.state.value.allInstances()[0].id)
        assertEquals("b", vm.state.value.allInstances()[1].id)
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
                    respond(listJson(*instances.toTypedArray()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(mockDaemonManager(client, wsMessages))
        vm.awaitState { !it.isLoading && it.allInstances().size == 2 }

        wsMessages.emit(WsMessage(
            type = WsMessage.INSTANCE_DELETED, instanceId = "b",
            payload = TestJson.encodeToJsonElement(mapOf("id" to "b")),
            timestamp = Instant.fromEpochMilliseconds(0),
        ))
        vm.awaitState { it.allInstances().size == 1 }
        assertEquals("a", vm.state.value.allInstances()[0].id)
    }

    @Test
    fun `empty sessions does not stall loading`() = runBlocking {
        val tempDir = createTempDirectory("conduit-test-empty")
        val manager = DaemonManager(configDir = tempDir)
        val vm = InstanceListViewModel(manager)
        val s = vm.awaitState { !it.isLoading }
        assertTrue(s.daemonGroups.isEmpty())
    }

    @Test
    fun `STATE_CHANGED event updates instance state in list`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val inst1 = sampleInstance("a", "Server A", InstanceState.STOPPED)
        val inst2 = sampleInstance("b", "Server B", InstanceState.STOPPED)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> respond(listJson(inst1, inst2), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(mockDaemonManager(client, wsMessages))
        vm.awaitState { it.allInstances().size == 2 }

        wsMessages.emit(WsMessage(
            type = WsMessage.STATE_CHANGED, instanceId = "a",
            payload = TestJson.encodeToJsonElement(StateChangedPayload(oldState = InstanceState.STOPPED, newState = InstanceState.RUNNING)),
            timestamp = Instant.fromEpochMilliseconds(0),
        ))
        vm.awaitState { it.allInstances().find { i -> i.id == "a" }?.state == InstanceState.RUNNING }
        assertEquals(InstanceState.RUNNING, vm.state.value.allInstances().find { it.id == "a" }?.state)
        assertEquals(InstanceState.STOPPED, vm.state.value.allInstances().find { it.id == "b" }?.state)
    }

    @Test
    fun `connectionState change triggers DaemonGroup recomposition`() = runBlocking {
        val inst = sampleInstance("a", "Server A")
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> respond(listJson(inst), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client)

        // Replace DaemonSession._connectionState with a controllable flow before ViewModel creation
        val connStateFlow = MutableStateFlow(WsConnectionState.DISCONNECTED)
        val session = manager.getSession(TEST_DAEMON_ID)!!
        val connField = DaemonSession::class.java.getDeclaredField("_connectionState")
        connField.isAccessible = true
        connField.set(session, connStateFlow)

        val vm = InstanceListViewModel(manager)
        vm.awaitState { !it.isLoading && it.daemonGroups.isNotEmpty() }
        assertEquals(WsConnectionState.DISCONNECTED, vm.state.value.daemonGroups[0].connectionState)

        connStateFlow.value = WsConnectionState.CONNECTED
        vm.awaitState { it.daemonGroups[0].connectionState == WsConnectionState.CONNECTED }

        connStateFlow.value = WsConnectionState.RECONNECTING
        vm.awaitState { it.daemonGroups[0].connectionState == WsConnectionState.RECONNECTING }
    }
}
