package dev.conduit.daemon

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.LoaderType
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.LoaderService
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TaskStatus
import dev.conduit.daemon.store.TaskStore
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Mock-HTTP unit tests for LoaderService.installFabric / installQuilt. Fast (<2s total),
 * covers all logic branches: happy paths (stable installer + no-stable fallback), installer
 * list empty, and download HTTP 404. The real-network smoke path lives in
 * ModLoaderInstallE2ETest and requires CONDUIT_RUN_SLOW_TESTS=true.
 */
class LoaderInstallMockTest {

    // ---- Fabric: happy paths ----

    @Test
    fun `fabric install writes server jar and persists loader info (stable installer)`() = runBlocking {
        val bytes = "fake-fabric-stable-jar".toByteArray()
        withStoppedInstance { ctx ->
            useLoaderService(
                ctx,
                createMockLoaderInstallHttpClient(
                    fabricServerJarResponse = MockLoaderResponse.Bytes(bytes),
                ),
            ) { svc ->
                val taskId = svc.install(ctx.instanceId, LoaderType.FABRIC, "0.16.14")
                awaitTaskDone(ctx.taskStore, taskId)

                val task = ctx.taskStore.get(taskId) ?: fail("task vanished")
                assertEquals(TaskStatus.DONE, task.status, "expected DONE, got ${task.status} (message=${task.message})")

                val jarPath = ctx.dataDir.serverJarPath(ctx.instanceId)
                assertTrue(jarPath.exists(), "server.jar should exist at $jarPath")
                assertContentEquals(bytes, jarPath.readBytes(), "server.jar bytes should match mocked response")

                val loader = assertNotNull(ctx.store.getLoader(ctx.instanceId), "loader should be persisted")
                assertEquals(LoaderType.FABRIC, loader.type)
                assertEquals("0.16.14", loader.version)
                assertEquals("1.20.4", loader.mcVersion)
            }
        }
    }

    @Test
    fun `fabric install falls back to first installer when no stable exists`() = runBlocking {
        // LoaderService.kt:185-186: firstOrNull { it.stable } ?: firstOrNull()
        // If every installer has stable=false, the fallback branch must still pick the first entry and succeed.
        val bytes = "fake-fabric-nostable-jar".toByteArray()
        val allUnstableJson = """[{"version":"1.0.0","stable":false},{"version":"0.9.0","stable":false}]"""
        withStoppedInstance { ctx ->
            useLoaderService(
                ctx,
                createMockLoaderInstallHttpClient(
                    installerVersionsJson = allUnstableJson,
                    fabricServerJarResponse = MockLoaderResponse.Bytes(bytes),
                ),
            ) { svc ->
                val taskId = svc.install(ctx.instanceId, LoaderType.FABRIC, "0.16.14")
                awaitTaskDone(ctx.taskStore, taskId)

                val task = ctx.taskStore.get(taskId) ?: fail("task vanished")
                assertEquals(
                    TaskStatus.DONE, task.status,
                    "fallback to first installer should succeed (message=${task.message})",
                )
                assertContentEquals(bytes, ctx.dataDir.serverJarPath(ctx.instanceId).readBytes())
            }
        }
    }

    // ---- Fabric: failure paths ----

    @Test
    fun `fabric install task fails when installer version list is empty`() = runBlocking {
        // LoaderService.kt:187: throw RuntimeException("No Fabric installer version found")
        withStoppedInstance { ctx ->
            useLoaderService(
                ctx,
                createMockLoaderInstallHttpClient(installerVersionsJson = "[]"),
            ) { svc ->
                val taskId = svc.install(ctx.instanceId, LoaderType.FABRIC, "0.16.14")
                awaitTaskDone(ctx.taskStore, taskId)

                val task = ctx.taskStore.get(taskId) ?: fail("task vanished")
                assertEquals(TaskStatus.ERROR, task.status)
                assertTrue(
                    task.message.contains("No Fabric installer version found"),
                    "expected failure message to mention the missing installer, got: ${task.message}",
                )
                assertNull(ctx.store.getLoader(ctx.instanceId), "failed install must not persist LoaderInfo")
                assertFalse(ctx.dataDir.serverJarPath(ctx.instanceId).exists(), "server.jar must not be written on failure")
            }
        }
    }

    @Test
    fun `fabric install task fails when server jar download returns 404`() = runBlocking {
        withStoppedInstance { ctx ->
            useLoaderService(
                ctx,
                createMockLoaderInstallHttpClient(
                    fabricServerJarResponse = MockLoaderResponse.Status(HttpStatusCode.NotFound),
                ),
            ) { svc ->
                val taskId = svc.install(ctx.instanceId, LoaderType.FABRIC, "bogus-version")
                awaitTaskDone(ctx.taskStore, taskId)

                val task = ctx.taskStore.get(taskId) ?: fail("task vanished")
                assertEquals(TaskStatus.ERROR, task.status)
                assertTrue(
                    task.message.contains("Fabric server jar") || task.message.contains("404"),
                    "failure message should point to the Fabric download, got: ${task.message}",
                )
                assertNull(ctx.store.getLoader(ctx.instanceId))
                assertFalse(ctx.dataDir.serverJarPath(ctx.instanceId).exists())
            }
        }
    }

    // ---- Quilt: installer download failure ----
    //
    // Quilt happy path cannot be mocked at this layer: installQuilt() now shells out to the
    // Quilt Server Installer jar (a Java subprocess) which we can't intercept with MockEngine.
    // Real-network coverage lives in ModLoaderInstallE2ETest.Quilt install produces server jar.
    // The single mock below verifies that a pre-subprocess failure (installer URL unreachable)
    // propagates correctly to the task layer.

    @Test
    fun `quilt install task fails when installer download returns 404`() = runBlocking {
        // maven.quiltmc.org returns 404 for the pinned installer jar. Task should fail before
        // the ProcessBuilder call -- no subprocess needed to exercise this path.
        withStoppedInstance { ctx ->
            useLoaderService(
                ctx,
                createMockLoaderInstallHttpClient(
                    quiltInstallerResponse = MockLoaderResponse.Status(HttpStatusCode.NotFound),
                ),
            ) { svc ->
                val taskId = svc.install(ctx.instanceId, LoaderType.QUILT, "0.20.0-beta.9")
                awaitTaskDone(ctx.taskStore, taskId)

                val task = ctx.taskStore.get(taskId) ?: fail("task vanished")
                assertEquals(TaskStatus.ERROR, task.status)
                assertTrue(
                    task.message.contains("Failed to download installer") || task.message.contains("404"),
                    "failure message should point to the installer download, got: ${task.message}",
                )
                assertNull(ctx.store.getLoader(ctx.instanceId))
            }
        }
    }
}

// ---------- shared test scaffolding (file-private) ----------

private data class TestCtx(
    val instanceId: String,
    val store: InstanceStore,
    val taskStore: TaskStore,
    val dataDir: DataDirectory,
    val scope: CoroutineScope,
)

private inline fun withStoppedInstance(block: (TestCtx) -> Unit) {
    val tempDir = Files.createTempDirectory("conduit-loader-mock")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        val dataDir = DataDirectory(tempDir)
        val store = InstanceStore(dataDir)
        val inst = store.create(CreateInstanceRequest(name = "FabricQuiltMock", mcVersion = "1.20.4"))
        store.markInitialized(inst.id)
        val broadcaster = WsBroadcaster(AppJson)
        val taskStore = TaskStore(broadcaster, AppJson)
        block(TestCtx(inst.id, store, taskStore, dataDir, scope))
    } finally {
        scope.cancel()
        tempDir.toFile().deleteRecursively()
    }
}

private inline fun useLoaderService(
    ctx: TestCtx,
    httpClient: HttpClient,
    block: (LoaderService) -> Unit,
) {
    val svc = LoaderService(
        instanceStore = ctx.store,
        dataDirectory = ctx.dataDir,
        taskStore = ctx.taskStore,
        scope = ctx.scope,
        httpClient = httpClient,
    )
    try {
        block(svc)
    } finally {
        svc.close()
    }
}

private suspend fun awaitTaskDone(taskStore: TaskStore, taskId: String, timeoutMs: Long = 5_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val status = taskStore.get(taskId)?.status
        if (status == TaskStatus.DONE || status == TaskStatus.ERROR) return
        delay(25)
    }
    fail("Task $taskId did not complete within ${timeoutMs}ms (status=${taskStore.get(taskId)?.status})")
}
