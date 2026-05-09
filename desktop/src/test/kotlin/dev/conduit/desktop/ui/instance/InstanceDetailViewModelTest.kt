package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.*
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.coroutines.withTimeout
import kotlin.test.*
import kotlin.time.Instant

class InstanceDetailViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
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
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
        vm.awaitLoad()

        vm.startServer()
        waitFor { vm.state.value.showEulaDialog }

        assertTrue(vm.state.value.showEulaDialog)
    }

    @Test
    fun `restartServer with eula not accepted shows dialog`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
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
                    mockJsonBody(EulaResponse(accepted = false)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
        vm.awaitLoad()

        vm.restartServer()
        waitFor { vm.state.value.showEulaDialog }

        assertTrue(vm.state.value.showEulaDialog)
    }

    @Test
    fun `restartServer success updates state`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
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
                "/api/v1/instances/test-inst/server/restart" -> respond(
                    mockJsonBody(
                        ServerStatusResponse(
                            state = InstanceState.STARTING,
                            playerCount = 0,
                            maxPlayers = 20,
                            players = emptyList(),
                            uptime = 0,
                            mcVersion = "1.20.4",
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
        vm.awaitLoad()

        vm.restartServer()
        vm.awaitAction()

        assertEquals(InstanceState.STARTING, vm.state.value.instance?.state)
        assertNull(vm.state.value.error)
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
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
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
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
        vm.awaitLoad()

        vm.deleteInstance()
        waitFor { vm.state.value.error != null || vm.state.value.isDeleted }

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Instance must be stopped before deletion"))
        assertFalse(vm.state.value.isDeleted)
    }

    @Test
    fun `PLAYERS_CHANGED updates player counts`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
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
                "/api/v1/instances/test-inst/server/status" -> respond(
                    mockJsonBody(
                        ServerStatusResponse(
                            state = InstanceState.RUNNING,
                            playerCount = 0,
                            maxPlayers = 20,
                            players = emptyList(),
                            uptime = 60,
                            mcVersion = "1.20.4",
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        val payloadJson = TestJson.encodeToJsonElement(
            PlayersChangedPayload(playerCount = 3, maxPlayers = 20)
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.PLAYERS_CHANGED,
                instanceId = "test-inst",
                payload = payloadJson,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )

        waitFor { vm.state.value.instance?.playerCount == 3 }
        assertEquals(3, vm.state.value.instance?.playerCount)
        assertEquals(20, vm.state.value.instance?.maxPlayers)
    }

    @Test
    fun `playerNames loaded when instance is RUNNING`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
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
                "/api/v1/instances/test-inst/server/status" -> respond(
                    mockJsonBody(
                        ServerStatusResponse(
                            state = InstanceState.RUNNING,
                            playerCount = 2,
                            maxPlayers = 20,
                            players = listOf("Steve", "Alex"),
                            uptime = 60,
                            mcVersion = "1.20.4",
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, mockDaemonManager(client))
        vm.awaitLoad()

        waitFor { vm.state.value.playerNames.isNotEmpty() }
        assertEquals(listOf("Steve", "Alex"), vm.state.value.playerNames)
    }

    @Test
    fun `playerNames cleared on server stop`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.RUNNING,
                            mcVersion = "1.20.4", mcPort = 25565, playerCount = 2, maxPlayers = 20,
                            createdAt = Instant.fromEpochMilliseconds(0),
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/server/eula" -> respond(
                    mockJsonBody(EulaResponse(accepted = true)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                "/api/v1/instances/test-inst/server/status" -> respond(
                    mockJsonBody(
                        ServerStatusResponse(
                            state = InstanceState.RUNNING,
                            playerCount = 2,
                            maxPlayers = 20,
                            players = listOf("Steve"),
                            uptime = 60,
                            mcVersion = "1.20.4",
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        waitFor { vm.state.value.playerNames.isNotEmpty() }
        assertEquals(listOf("Steve"), vm.state.value.playerNames)

        val payloadJson = TestJson.encodeToJsonElement(
            StateChangedPayload(oldState = InstanceState.RUNNING, newState = InstanceState.STOPPED)
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.STATE_CHANGED,
                instanceId = "test-inst",
                payload = payloadJson,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )

        waitFor { vm.state.value.playerNames.isEmpty() }
        assertTrue(vm.state.value.playerNames.isEmpty())
    }

    @Test
    fun `console restored from session on init`() = runBlocking {
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
        val manager = mockDaemonManager(client)
        val session = manager.getSession(TEST_DAEMON_ID)!!
        session.appendConsoleLine("test-inst", "old line")

        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        assertEquals(listOf("old line"), vm.state.value.consoleLines)
    }

    @Test
    fun `TASK_PROGRESS updates installProgress and installMessage`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.INITIALIZING,
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
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        assertEquals(null, vm.state.value.installProgress)

        val progressPayload = TestJson.encodeToJsonElement(
            TaskProgressPayload(taskId = "task-1", progress = 0.42, message = "Downloading...")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_PROGRESS,
                instanceId = "test-inst",
                payload = progressPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )

        waitFor { vm.state.value.installProgress != null }
        assertEquals(0.42, vm.state.value.installProgress)
        assertEquals("Downloading...", vm.state.value.installMessage)
    }

    @Test
    fun `TASK_COMPLETED success clears installProgress`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.INITIALIZING,
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
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        // First set progress
        val progressPayload = TestJson.encodeToJsonElement(
            TaskProgressPayload(taskId = "task-1", progress = 0.8, message = "Installing...")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_PROGRESS,
                instanceId = "test-inst",
                payload = progressPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.installProgress == 0.8 }

        // Then task completes
        val completedPayload = TestJson.encodeToJsonElement(
            TaskCompletedPayload(taskId = "task-1", success = true, message = "Done")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_COMPLETED,
                instanceId = "test-inst",
                payload = completedPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )

        waitFor { vm.state.value.installProgress == null }
        assertNull(vm.state.value.installProgress)
        assertNull(vm.state.value.installMessage)
    }

    @Test
    fun `TASK_COMPLETED failure sets installError`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.INITIALIZING,
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
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        val progressPayload = TestJson.encodeToJsonElement(
            TaskProgressPayload(taskId = "task-1", progress = 0.3, message = "Downloading...")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_PROGRESS,
                instanceId = "test-inst",
                payload = progressPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.installProgress == 0.3 }

        val completedPayload = TestJson.encodeToJsonElement(
            TaskCompletedPayload(taskId = "task-1", success = false, message = "Download failed: 404")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_COMPLETED,
                instanceId = "test-inst",
                payload = completedPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )

        waitFor { vm.state.value.installError != null }
        assertEquals("Download failed: 404", vm.state.value.installError)
        assertNull(vm.state.value.installProgress)
    }

    @Test
    fun `cancelTask calls API with tracked taskId`() = runBlocking {
        val wsMessages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)
        var cancelCalled = false
        val httpClient = mockHttpClient { request ->
            when {
                request.url.encodedPath == "/api/v1/instances/test-inst" -> respond(
                    mockJsonBody(
                        InstanceSummary(
                            id = "test-inst", name = "My Server", state = InstanceState.INITIALIZING,
                            mcVersion = "1.20.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
                            createdAt = Instant.fromEpochMilliseconds(0),
                        )
                    ),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.encodedPath == "/api/v1/instances/test-inst/server/eula" -> respond(
                    mockJsonBody(EulaResponse(accepted = true)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/tasks/task-1/cancel" -> {
                    cancelCalled = true
                    respond(
                        """{"cancelled":true}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val manager = mockDaemonManager(client, wsMessages)
        val vm = InstanceDetailViewModel("test-inst", TEST_DAEMON_ID, manager)
        vm.awaitLoad()

        // Emit TASK_PROGRESS to establish taskId
        val progressPayload = TestJson.encodeToJsonElement(
            TaskProgressPayload(taskId = "task-1", progress = 0.5, message = "Downloading...")
        )
        wsMessages.emit(
            WsMessage(
                type = WsMessage.TASK_PROGRESS,
                instanceId = "test-inst",
                payload = progressPayload,
                timestamp = Instant.fromEpochMilliseconds(0),
            )
        )
        waitFor { vm.state.value.installProgress == 0.5 }

        vm.cancelTask()

        waitFor { cancelCalled }
        assertTrue(cancelCalled, "Expected POST /api/v1/tasks/task-1/cancel to be called")
    }
}
