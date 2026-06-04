package dev.conduit.desktop.ui.instance

import dev.conduit.core.model.*
import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class CreateInstanceViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
    }

    private suspend fun CreateInstanceViewModel.awaitDone(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isCreating) delay(20)
        }
    }

    private suspend fun CreateInstanceViewModel.awaitVersions(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.versionsLoading) delay(20)
        }
    }

    /** Creates a mock HTTP client that handles versions + instance creation. */
    private fun createMockClient() = mockHttpClient { request ->
        when {
            // GET /api/v1/minecraft/versions — needed by init
            request.url.encodedPath.contains("/minecraft/versions") && request.method == HttpMethod.Get -> {
                respond(
                    content = mockJsonBody(
                        MinecraftVersionsResponse(
                            versions = listOf(
                                MinecraftVersion(id = "1.21.4", type = "release", releaseTime = "2024-12-03"),
                                MinecraftVersion(id = "1.20.4", type = "release", releaseTime = "2023-12-07"),
                            ),
                            cachedAt = "2026-01-01T00:00:00Z",
                        ),
                    ),
                    status = HttpStatusCode.OK,
                )
            }
            // GET /api/v1/minecraft/{version}/loaders — needed by init
            request.url.encodedPath.contains("/loaders") && request.method == HttpMethod.Get -> {
                respond(
                    content = mockJsonBody(
                        listOf(
                            AvailableLoader(type = LoaderType.NEOFORGE, versions = listOf("20.4.237")),
                            AvailableLoader(type = LoaderType.FABRIC, versions = listOf("0.16.14")),
                        ),
                    ),
                    status = HttpStatusCode.OK,
                )
            }
            // POST /api/v1/instances — create instance
            request.url.encodedPath.endsWith("/instances") && request.method == HttpMethod.Post -> {
                respond(
                    content = mockJsonBody(
                        InstanceSummary(
                            id = "inst-1", name = "Test", state = InstanceState.INITIALIZING,
                            mcVersion = "1.21.4", mcPort = 25565, playerCount = 0, maxPlayers = 20,
                            createdAt = kotlin.time.Clock.System.now(),
                        ),
                    ),
                    status = HttpStatusCode.Created,
                )
            }
            // POST /api/v1/instances/{id}/loader/install — install loader
            request.url.encodedPath.contains("/loader/install") && request.method == HttpMethod.Post -> {
                respond(
                    content = mockJsonBody(TaskResponse(taskId = "task-1", type = "loader_install", message = "Installing")),
                    status = HttpStatusCode.Accepted,
                )
            }
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `initial state has correct defaults`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.awaitVersions()

        val s = vm.state.value
        assertEquals("", s.name)
        assertEquals(25565, s.port)
        assertEquals(20, s.maxPlayers)
        assertEquals(LoaderDisplayType.NEOFORGE, s.loaderType)
        assertEquals("Test Daemon", s.daemonName)
        assertEquals("1.21.4", s.mcVersion) // auto-selected latest
    }

    @Test
    fun `updateName changes state`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.updateName("My Server")
        assertEquals("My Server", vm.state.value.name)
    }

    @Test
    fun `updatePort changes state`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.updatePort("25566")
        assertEquals(25566, vm.state.value.port)
    }

    @Test
    fun `updatePort ignores non-numeric input`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.updatePort("abc")
        assertEquals(25565, vm.state.value.port) // unchanged
    }

    @Test
    fun `updateMaxPlayers changes state`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.updateMaxPlayers("50")
        assertEquals(50, vm.state.value.maxPlayers)
    }

    @Test
    fun `updateLoaderType changes type`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.updateLoaderType(LoaderDisplayType.FABRIC)
        assertEquals(LoaderDisplayType.FABRIC, vm.state.value.loaderType)
    }

    @Test
    fun `createInstance with empty name shows error`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))

        var onSuccessCalled = false
        vm.create { onSuccessCalled = true }
        vm.awaitDone()

        assertFalse(onSuccessCalled)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `create success with Vanilla (no loader install)`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.awaitVersions()
        vm.updateName("Test")
        vm.updateLoaderType(LoaderDisplayType.VANILLA)

        var onSuccessCalled = false
        vm.create { onSuccessCalled = true }
        vm.awaitDone()

        assertTrue(onSuccessCalled)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `create success with NeoForge (installs loader)`() = runBlocking {
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(createMockClient())))
        vm.awaitVersions()
        vm.updateName("Test")
        vm.updateLoaderType(LoaderDisplayType.NEOFORGE)
        vm.updateLoaderVersion("20.4.237")

        var onSuccessCalled = false
        vm.create { onSuccessCalled = true }
        vm.awaitDone()

        assertTrue(onSuccessCalled)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `toBackendType maps correctly`() {
        assertEquals(LoaderType.NEOFORGE, CreateInstanceViewModel.toBackendType(LoaderDisplayType.NEOFORGE))
        assertEquals(LoaderType.FABRIC, CreateInstanceViewModel.toBackendType(LoaderDisplayType.FABRIC))
        assertEquals(LoaderType.QUILT, CreateInstanceViewModel.toBackendType(LoaderDisplayType.QUILT))
        assertEquals(LoaderType.FORGE, CreateInstanceViewModel.toBackendType(LoaderDisplayType.FORGE))
        assertNull(CreateInstanceViewModel.toBackendType(LoaderDisplayType.VANILLA))
    }
}
