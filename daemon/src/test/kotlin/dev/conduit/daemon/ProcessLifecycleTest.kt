package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ProcessLifecycleTest {

    private fun stuckServerJvmConfig(): Pair<String, List<String>> {
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        return Pair(javaPath, listOf("-cp", classpath, StuckMcServer::class.qualifiedName!!))
    }

    @Test
    fun `startup timeout forces STOPPED when Done never appears`() = runBlocking {
        // Ktor 3.4.3's testApplication enforces a 60s runtime timeout that collides with our 75s
        // STOPPED-poll. We wrap runTestApplication in runBlocking to bypass it. TODO: revisit when
        // Ktor offers a per-test-application timeout override (track Ktor issue KTOR-<xxx>).
        val tempDir = Files.createTempDirectory("conduit-timeout")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = stuckServerJvmConfig()

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Stuck Test", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                // Wait for init to finish
                val deadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < deadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                val startNanos = System.nanoTime()
                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Poll up to 75s: 60s timeout + 15s slack for monitorJob cleanup
                val stoppedDeadline = System.currentTimeMillis() + 75_000
                var finalState: InstanceState? = null
                var finalMessage: String? = null
                var elapsedMs: Long = -1
                while (System.currentTimeMillis() < stoppedDeadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.STOPPED) {
                        finalState = s.state
                        finalMessage = s.statusMessage
                        elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                        break
                    }
                    delay(500)
                }
                assertEquals(InstanceState.STOPPED, finalState, "Expected timeout to force STOPPED")
                assertNotNull(finalMessage, "Expected a statusMessage explaining timeout")
                assertTrue(
                    finalMessage.contains("timed out", ignoreCase = true),
                    "Expected statusMessage to mention timeout, got: $finalMessage"
                )
                assertTrue(
                    elapsedMs in 55_000..75_000,
                    "Expected timeout to fire near 60s (got ${elapsedMs}ms) — bug if much shorter or longer"
                )
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `concurrent start requests return POWER_LOCKED for loser`() = runBlocking {
        // Ktor 3.4.3's testApplication 60s timeout workaround (see class-level TODO).
        val tempDir = Files.createTempDirectory("conduit-lock")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Lock Test", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val deadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < deadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                val (javaPath, jvmArgs) = stuckServerJvmConfig()
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                // Fire two concurrent start requests
                val results = listOf(
                    async { client.post("/api/v1/instances/${inst.id}/server/start") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    } },
                    async { client.post("/api/v1/instances/${inst.id}/server/start") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    } },
                ).awaitAll()

                val successes = results.count { it.status == HttpStatusCode.OK }
                val conflicts = results.count { it.status == HttpStatusCode.Conflict }
                assertEquals(1, successes, "Expected exactly one success")
                assertEquals(1, conflicts, "Expected exactly one conflict")

                val loser = results.first { it.status == HttpStatusCode.Conflict }
                val error = loser.body<ErrorResponse>()
                assertTrue(
                    error.error.code in listOf("POWER_LOCKED", "SERVER_ALREADY_RUNNING"),
                    "Expected POWER_LOCKED or SERVER_ALREADY_RUNNING, got ${error.error.code}"
                )

                // Cleanup: kill the stuck server to avoid 60s timeout delay
                client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `mutex prevents concurrent start calls at manager level`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-mutex")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val scope = kotlinx.coroutines.CoroutineScope(
                SupervisorJob() + Dispatchers.IO
            )
            val broadcaster = dev.conduit.daemon.service.WsBroadcaster(AppJson)
            val manager = dev.conduit.daemon.service.ServerProcessManager(
                store, DataDirectory(tempDir), broadcaster, scope, json = AppJson
            )

            // Create a STOPPED instance directly
            val inst = store.create(CreateInstanceRequest(name = "MutexUnit", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            val (javaPath, jvmArgs) = stuckServerJvmConfig()
            store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

            val lockRejections = AtomicInteger(0)
            val stateRejections = AtomicInteger(0)
            val jobs = (1..5).map {
                scope.launch {
                    try { manager.start(inst.id) } catch (e: ApiException) {
                        when (e.code) {
                            "POWER_LOCKED" -> lockRejections.incrementAndGet()
                            "SERVER_ALREADY_RUNNING" -> stateRejections.incrementAndGet()
                        }
                    }
                }
            }
            jobs.forEach { it.join() }

            assertEquals(4, lockRejections.get() + stateRejections.get(), "Expected 4 of 5 calls to be rejected")
            assertTrue(manager.isRunning(inst.id), "Exactly one process should be running")

            manager.kill(inst.id)
            manager.awaitProcessExit(inst.id)
            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
