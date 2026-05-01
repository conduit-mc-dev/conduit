package dev.conduit.daemon

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.LoaderType
import dev.conduit.core.model.InstanceState
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Exploratory test documenting what cancellation infrastructure is needed.
 *
 * ## Current state (no cancellation infrastructure)
 *
 * 1. **TaskStore** has no `cancel()` method. Statuses are RUNNING, DONE, ERROR only.
 *    A new CANCELLED status is needed.
 *
 * 2. **LoaderService.install()** launches work via `scope.launch` but discards the Job.
 *    The Job must be stored (e.g. in a ConcurrentHashMap<taskId, Job>) so it can be
 *    cancelled. On cancellation, partial files (installer.jar, server.jar from
 *    mid-download) must be deleted in a `finally` block.
 *
 * 3. **PackService.build()** launches work via `scope.launch` but discards the Job.
 *    Same fix: store Job, add cancel method. On cancellation, the partial pack.mrpack
 *    file and pack directory must be cleaned up.
 *
 * 4. **No cancel endpoint in routes.** A `DELETE /api/v1/tasks/{taskId}` or
 *    `POST /api/v1/tasks/{taskId}/cancel` endpoint is needed that:
 *    - Looks up the Job for the task
 *    - Calls job.cancel()
 *    - On cancellation, triggers file cleanup and reverts instance/pack state
 *
 * 5. **Instance state considerations:**
 *    - Loader install: instance is STOPPED during install (enforced by guard). After
 *      cancellation, it should remain STOPPED and have no loader info set.
 *    - Pack build: BuildState should revert from BUILDING/ERROR back to IDLE or at
 *      minimum not leave a half-written .mrpack file.
 *
 * ## What this file does
 *
 * Before any infrastructure exists, the single test below (@Ignore-d) documents the
 * desired behavior. Once the infrastructure is built, the @Ignore annotation can be
 * removed and the test should pass.
 *
 * The exploratory test is written against the LoaderService directly (unit-test style
 * like LoaderInstallMockTest) so it exercises the service layer without needing a
 * full HTTP stack.
 */
class CancellationCleanupTest {

    /**
     * Exploratory test: cancel a loader install mid-operation and verify cleanup.
     *
     * Infrastructure needed before this test can pass:
     *
     *   - TaskStore: add `CANCELLED` to TaskStatus enum, add `cancel(taskId, message)`
     *     method that transitions RUNNING -> CANCELLED and broadcasts.
     *
     *   - LoaderService: store the Job from scope.launch keyed by taskId (e.g.
     *     `private val installJobs = ConcurrentHashMap<String, Job>()`). Expose a
     *     `cancelInstall(taskId)` that calls job.cancel() and deletes partial files.
     *
     *   - LoaderService.install(): wrap the download/write logic in a try/finally that
     *     cleans up partial files on cancellation (installer.jar, server.jar in the
     *     instance directory).
     *
     *   - Routes: add `DELETE /api/v1/tasks/{taskId}` that delegates to
     *     LoaderService.cancelInstall() or PackService.cancelBuild() based on task type.
     */
    @Test
    @Ignore("Requires cancellation infrastructure: TaskStore.cancel(), Job tracking in services, file cleanup hooks")
    fun `cancel loader install cleans up partial files and reverts instance state`() = runBlocking {
        // ---------- setup (same pattern as LoaderInstallMockTest) ----------
        val tempDir = Files.createTempDirectory("conduit-cancel-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val inst = store.create(CreateInstanceRequest(name = "CancelTest", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            val broadcaster = WsBroadcaster(AppJson)
            val taskStore = TaskStore(broadcaster, AppJson)

            // Use a mock HTTP client that simulates a slow download so we have time to cancel.
            // The download returns bytes after a delay, giving us a window to cancel.
            val slowBytes = "slow-fabric-jar-content".toByteArray()
            val httpClient = createMockLoaderInstallHttpClient(
                fabricServerJarResponse = MockLoaderResponse.Bytes(slowBytes),
            )

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

                // Let the download start but not complete (simulate mid-operation cancel).
                delay(50)

                // ---------- cancel (infrastructure needed) ----------
                //
                // Once built, this would look like:
                //   svc.cancelInstall(taskId)
                // or via HTTP:
                //   client.delete("/api/v1/tasks/$taskId") { header("Authorization", "Bearer $token") }
                //
                // For now, we simulate what the cancel handler would do:
                //   1. Cancel the install Job
                //   2. Clean up partial files (installer.jar, server.jar)
                //   3. TaskStore marks task as CANCELLED

                // Wait for cancellation to propagate.
                awaitTaskDone(taskStore, taskId)

                // ---------- verify cleanup ----------
                val finalTask = taskStore.get(taskId)
                assertNotNull(finalTask, "task should still exist after cancellation")

                // EXPECTED (after infrastructure):
                // assertEquals(TaskStatus.CANCELLED, finalTask.status)

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

    // ---------- helper ----------

    private suspend fun awaitTaskDone(taskStore: TaskStore, taskId: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = taskStore.get(taskId)?.status
            if (status == TaskStatus.DONE || status == TaskStatus.ERROR) return
            delay(25)
        }
        fail("Task $taskId did not complete within ${timeoutMs}ms (status=${taskStore.get(taskId)?.status})")
    }
}
