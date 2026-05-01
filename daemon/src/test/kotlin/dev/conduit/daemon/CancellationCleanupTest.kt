package dev.conduit.daemon

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.InstalledMod
import dev.conduit.core.model.LoaderInfo
import dev.conduit.core.model.LoaderType
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.ModHashes
import dev.conduit.core.model.ModEnvSupport
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.LoaderService
import dev.conduit.daemon.service.PackService
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.BuildState
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import dev.conduit.daemon.store.PackStore
import dev.conduit.daemon.store.TaskStatus
import dev.conduit.daemon.store.TaskStore
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests that cancellation infrastructure correctly cleans up partial files
 * and reverts state when an async task (loader install, pack build) is cancelled.
 */
class CancellationCleanupTest {

    /**
     * Cancels a loader install mid-operation and verifies:
     * - Task status becomes CANCELLED
     * - No loader info persisted
     * - No stray JAR files (server.jar, installer.jar, installer.jar.log)
     * - Instance state remains STOPPED
     */
    @Test
    fun `cancel loader install cleans up partial files and reverts instance state`() = runBlocking {
        // ---------- setup ----------
        val tempDir = Files.createTempDirectory("conduit-cancel-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val inst = store.create(CreateInstanceRequest(name = "CancelTest", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            val broadcaster = WsBroadcaster(AppJson)
            val taskStore = TaskStore(broadcaster, AppJson)

            // Use a mock HTTP client that delays to simulate a slow download,
            // giving us time to cancel mid-operation.
            val slowBytes = "slow-fabric-jar-content".toByteArray()
            val httpClient = HttpClient(MockEngine { request ->
                Thread.sleep(200) // simulate slow download so cancellation can happen
                val url = request.url.toString()
                when {
                    url.contains("meta.fabricmc.net") && url.contains("/versions/installer") ->
                        respond(
                            """[{"version":"1.0.1","stable":true}]""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    url.contains("meta.fabricmc.net") && url.contains("/server/jar") ->
                        respond(slowBytes, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                    else -> respondError(HttpStatusCode.NotFound)
                }
            })

            val svc = LoaderService(
                instanceStore = store,
                dataDirectory = dataDir,
                taskStore = taskStore,
                scope = scope,
                httpClient = httpClient,
            )

            try {
                // ---------- start install ----------
                val taskId = svc.install(inst.id, LoaderType.FABRIC, "0.16.14")
                val task = taskStore.get(taskId)
                assertNotNull(task, "task should exist immediately after install()")
                assertEquals(TaskStatus.RUNNING, task.status)

                // Let the download start but not complete.
                delay(50)

                // ---------- cancel ----------
                svc.cancelInstall(taskId)

                // Wait for cancellation to propagate.
                awaitTaskDone(taskStore, taskId)

                // ---------- verify cleanup ----------
                val finalTask = taskStore.get(taskId)
                assertNotNull(finalTask, "task should still exist after cancellation")
                assertEquals(TaskStatus.CANCELLED, finalTask.status)

                // No loader info persisted (install never completed).
                assertNull(store.getLoader(inst.id), "loader should NOT be set after cancelled install")

                // No stray JAR files.
                assertFalse(dataDir.serverJarPath(inst.id).exists(),
                    "server.jar should not exist after cancelled install")

                // Instance state remains STOPPED.
                assertEquals(InstanceState.STOPPED, store.get(inst.id).state,
                    "instance should remain STOPPED after cancelled install")

                // No installer.jar or installer.jar.log leftovers.
                val instanceDir = dataDir.instanceDir(inst.id)
                assertFalse(instanceDir.resolve("installer.jar").exists(),
                    "installer.jar should be cleaned up")
                assertFalse(instanceDir.resolve("installer.jar.log").exists(),
                    "installer.jar.log should be cleaned up")

            } finally {
                svc.close()
            }
        } finally {
            scope.cancel()
            tempDir.toFile().deleteRecursively()
        }
    }

    /**
     * Cancels a pack build mid-operation and verifies:
     * - Task status becomes CANCELLED
     * - Partial mrpack file is deleted
     * - buildState reverts to IDLE
     */
    @Test
    fun `cancel pack build cleans up partial mrpack and reverts state`() = runBlocking {
        // ---------- setup ----------
        val tempDir = Files.createTempDirectory("conduit-cancel-pack-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val inst = store.create(CreateInstanceRequest(name = "PackCancelTest", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            store.setLoader(inst.id, LoaderInfo(
                type = LoaderType.FABRIC, version = "0.15.6", mcVersion = "1.20.4"
            ))
            val broadcaster = WsBroadcaster(AppJson)
            val taskStore = TaskStore(broadcaster, AppJson)
            val modStore = ModStore()

            // Add some mods so there is work to do.
            repeat(5) { i ->
                modStore.add(inst.id, InstalledMod(
                    id = UUID.randomUUID().toString(),
                    source = "modrinth",
                    modrinthProjectId = "proj$i",
                    modrinthVersionId = "ver$i",
                    name = "TestMod$i",
                    version = "1.0.$i",
                    fileName = "testmod-$i.jar",
                    env = ModEnvSupport(client = "required", server = "required"),
                    hashes = ModHashes(sha1 = "abc$i", sha512 = "def$i"),
                    downloadUrl = "https://cdn.example.com/proj$i/ver$i/testmod-$i.jar",
                    fileSize = 1000L + i,
                    enabled = true,
                ))
            }

            val packStore = PackStore()
            val packService = PackService(
                modStore = modStore,
                instanceStore = store,
                packStore = packStore,
                dataDirectory = dataDir,
                taskStore = taskStore,
                scope = scope,
                json = AppJson,
            )
            packService.buildDelayMs = 200 // give cancellation time to happen mid-build

            val mrpackPath = dataDir.packMrpackPath(inst.id)

            // ---------- start build ----------
            val taskId = packService.build(inst.id, "1.0.1", "Test build")
            val task = taskStore.get(taskId)
            assertNotNull(task, "task should exist immediately after build()")
            assertEquals(TaskStatus.RUNNING, task.status)

            // Let the build start and hit the delay point.
            delay(50)

            // ---------- cancel ----------
            packService.cancelBuild(taskId)

            // Wait for cancellation to propagate.
            awaitTaskDone(taskStore, taskId)

            // ---------- verify cleanup ----------
            val finalTask = taskStore.get(taskId)
            assertNotNull(finalTask, "task should still exist after cancellation")
            assertEquals(TaskStatus.CANCELLED, finalTask.status)

            // Partial mrpack file should be deleted.
            assertFalse(mrpackPath.exists(), "pack.mrpack should not exist after cancelled build")

            // buildState should revert to IDLE.
            val packMeta = packStore.get(inst.id)
            assertNotNull(packMeta)
            assertEquals(BuildState.IDLE, packMeta.buildState, "buildState should revert to IDLE")
            assertEquals("", packMeta.buildMessage)

        } finally {
            scope.cancel()
            tempDir.toFile().deleteRecursively()
        }
    }

    // ---------- helper ----------

    private suspend fun awaitTaskDone(taskStore: TaskStore, taskId: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = taskStore.get(taskId)?.status
            if (status == TaskStatus.DONE || status == TaskStatus.ERROR || status == TaskStatus.CANCELLED) return
            delay(25)
        }
        fail("Task $taskId did not complete within ${timeoutMs}ms (status=${taskStore.get(taskId)?.status})")
    }
}
