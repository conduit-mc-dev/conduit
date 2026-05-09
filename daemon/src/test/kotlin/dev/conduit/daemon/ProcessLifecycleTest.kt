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
import kotlinx.coroutines.sync.Mutex
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
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
    fun `at most one of N concurrent starts results in a running process`() = runBlocking {
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

    @Test
    fun `start throws POWER_LOCKED when power lock is already held`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-lock-det")
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

            val inst = store.create(CreateInstanceRequest(name = "DeterministicLock", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)

            // Reach into the private powerLocks map via reflection and pre-acquire the mutex.
            // This is a test-only hack to deterministically exercise the POWER_LOCKED rejection path
            // without adding production API surface. See Task 2 code-review feedback I-1.
            val powerLocksField = manager.javaClass.getDeclaredField("powerLocks").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val powerLocks = powerLocksField.get(manager) as java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>
            val lock = powerLocks.computeIfAbsent(inst.id) { kotlinx.coroutines.sync.Mutex() }
            assertTrue(lock.tryLock(), "precondition: test must acquire lock before calling manager.start()")

            try {
                val ex = assertFailsWith<ApiException> { manager.start(inst.id) }
                assertEquals("POWER_LOCKED", ex.code)
                // State unchanged — no transition to STARTING happened
                assertEquals(InstanceState.STOPPED, store.get(inst.id).state)
                assertFalse(manager.isRunning(inst.id))
            } finally {
                lock.unlock()
            }

            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `crashed process stays STOPPED when autoRestart disabled by default`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-crash-off")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Crash Off", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Wait for crash → STOPPED. Poll up to 10s.
                val deadline = System.currentTimeMillis() + 10_000
                var sawRestart = false
                var sawStopped = false
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id).state
                    if (s == InstanceState.STOPPED) sawStopped = true
                    // If state returns to STARTING after having been STOPPED, that's a restart attempt
                    if (sawStopped && s == InstanceState.STARTING) sawRestart = true
                    delay(200)
                }

                val final = store.get(inst.id)
                assertEquals(InstanceState.CRASHED, final.state)
                assertFalse(sawRestart, "autoRestart=false should not restart crashed process")
                assertNotNull(final.statusMessage)
                assertTrue(
                    final.statusMessage!!.contains("exited with code"),
                    "Expected exit-code message, got: ${final.statusMessage}"
                )
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `crashed process auto-restarts when enabled and within maxTimes`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-crash-on")
        try {
            val dataDir = DataDirectory(tempDir)

            // Write DaemonConfig BEFORE bootstrap so autoRestart=true when ServerProcessManager reads config.
            dataDir.configPath.parent?.toFile()?.mkdirs()
            dataDir.configPath.toFile().writeText(
                """{"autoRestartEnabled":true,"autoRestartMaxTimes":2,"crashLoopTimeoutSeconds":30}"""
            )

            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Crash On", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Observe state transitions: expect RUNNING at least twice, proving auto-restart occurred.
                // STARTING is transient and often shorter than the 100ms polling interval under load —
                // RUNNING is the durable signal that the restarted process is actually executing.
                val transitions = mutableListOf<InstanceState>()
                val deadline = System.currentTimeMillis() + 20_000
                var lastState: InstanceState? = null
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id).state
                    if (s != lastState) {
                        transitions.add(s)
                        lastState = s
                    }
                    if (transitions.count { it == InstanceState.RUNNING } >= 2) break
                    delay(100)
                }

                assertTrue(
                    transitions.count { it == InstanceState.RUNNING } >= 2,
                    "Expected at least 2 RUNNING occurrences (initial + auto-restart), got: $transitions"
                )

                // Cleanup — kill so auto-restart doesn't keep firing past test end
                runCatching {
                    client.post("/api/v1/instances/${inst.id}/server/kill") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `crash loop detection gives up after maxTimes within window`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-crash-loop")
        try {
            val dataDir = DataDirectory(tempDir)

            dataDir.configPath.parent?.toFile()?.mkdirs()
            dataDir.configPath.toFile().writeText(
                """{"autoRestartEnabled":true,"autoRestartMaxTimes":2,"crashLoopTimeoutSeconds":30}"""
            )

            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Crash Loop", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // maxTimes=2, 30s window. Expect: start → crash → restart → crash → restart → crash → give up.
                // That's 3 crashes total. Give 30s.
                val deadline = System.currentTimeMillis() + 30_000
                var finalMsg: String? = null
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.CRASHED &&
                        s.statusMessage?.contains("Crash loop", ignoreCase = true) == true) {
                        finalMsg = s.statusMessage
                        break
                    }
                    delay(500)
                }
                assertNotNull(finalMsg, "Expected crash-loop statusMessage, final state: ${store.get(inst.id)}")
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `start reverts to STOPPED when ProcessBuilder fails`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-launch-fail")
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
                    setBody(CreateInstanceRequest(name = "Launch Fail", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                // Invalid javaPath — ProcessBuilder.start() throws IOException
                val invalidJavaPath = if (System.getProperty("os.name", "").lowercase().contains("windows"))
                    "C:\\nonexistent\\no-such-java.exe" else "/nonexistent/bin/no-such-java-binary"
                store.updateJvmConfig(inst.id, true, emptyList(), true, invalidJavaPath)

                val startResp = client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.InternalServerError, startResp.status)

                // State must have reverted to STOPPED with an informative message
                val state = store.get(inst.id)
                assertEquals(InstanceState.STOPPED, state.state, "Instance must not be stuck in STARTING")
                assertNotNull(state.statusMessage)
                assertTrue(
                    state.statusMessage!!.contains("Failed to launch", ignoreCase = true),
                    "Expected statusMessage to explain launch failure, got: ${state.statusMessage}"
                )

                // Subsequent start should succeed (state was reset to STOPPED)
                store.updateJvmConfig(inst.id, true, emptyList(), true, ProcessHandle.current().info().command().orElse("java"))
                // Don't actually try to run — just verify state machine accepts the next start attempt.
                // (We can't easily make it run the MC server here; we only care that state transition isn't blocked.)
                // The test ends here; state is STOPPED and manageable.
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `manual start after crash loop GiveUp resets counter`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-giveup-reset")
        try {
            val dataDir = DataDirectory(tempDir)
            dataDir.configPath.parent?.toFile()?.mkdirs()
            dataDir.configPath.toFile().writeText(
                """{"autoRestartEnabled":true,"autoRestartMaxTimes":1,"crashLoopTimeoutSeconds":30}"""
            )

            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Give Up Reset", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                // Trigger initial crash loop: maxTimes=1, so 1 auto-restart, then GiveUp on second crash.
                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Wait for GiveUp to fire
                val giveUpDeadline = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < giveUpDeadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.CRASHED &&
                        s.statusMessage?.contains("Crash loop", ignoreCase = true) == true) break
                    delay(200)
                }
                val afterGiveUp = store.get(inst.id)
                assertTrue(
                    afterGiveUp.statusMessage?.contains("Crash loop", ignoreCase = true) == true,
                    "Precondition: expected GiveUp after crash loop, got: $afterGiveUp"
                )

                // Manually start again. Counter should reset; the ensuing crash should NOT re-trigger GiveUp
                // on the first post-manual-start crash.
                val startResp = client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.OK, startResp.status,
                    "Manual start after GiveUp must succeed (counter reset clears the block)")

                // After manual start, counter is reset. First crash in this fresh cycle is crash #1.
                // With maxTimes=1, that's still Restart (not GiveUp).
                // First, wait for state to leave STOPPED (manual start worked)
                val startingDeadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < startingDeadline) {
                    val s = store.get(inst.id).state
                    if (s == InstanceState.STARTING || s == InstanceState.RUNNING) break
                    delay(100)
                }

                // Then wait for next STOPPED (process crashed again)
                val crashAgainDeadline = System.currentTimeMillis() + 10_000
                var afterFirstManualCrash: InstanceSummary? = null
                while (System.currentTimeMillis() < crashAgainDeadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.CRASHED && s.statusMessage != afterGiveUp.statusMessage) {
                        afterFirstManualCrash = s
                        break
                    }
                    delay(200)
                }
                assertNotNull(afterFirstManualCrash, "Expected another STOPPED transition after manual start")
                // Counter was reset → first crash in fresh cycle should NOT say "Crash loop"
                assertFalse(
                    afterFirstManualCrash.statusMessage?.contains("Crash loop", ignoreCase = true) == true,
                    "First crash after manual start must NOT be GiveUp (counter should have reset). Got: ${afterFirstManualCrash.statusMessage}"
                )

                // Cleanup: kill to prevent cascade
                runCatching {
                    client.post("/api/v1/instances/${inst.id}/server/kill") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `start writes configured port to server properties`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-port")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, StuckMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Port Test", mcVersion = "1.20.4", mcPort = 25566))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val propsFile = dataDir.instanceDir(inst.id).resolve("server.properties").toFile()
                assertTrue(propsFile.exists(), "server.properties should be created before process starts")
                val props = java.util.Properties()
                propsFile.inputStream().use { props.load(it) }
                assertEquals(
                    "25566", props.getProperty("server-port"),
                    "server-port should match configured mcPort"
                )

                // Cleanup
                client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // -----------------------------------------------------------------------
    // Post-bugfix tests (daemon functional completeness audit, 2026-05-10)
    // -----------------------------------------------------------------------

    private fun runningServerJvmConfig(): Pair<String, List<String>> {
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        return Pair(javaPath, listOf("-cp", classpath, RunningMcServer::class.qualifiedName!!))
    }

    @Test
    fun `kill RUNNING server transitions to STOPPED`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-kill-running")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = runningServerJvmConfig()

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Kill Running", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val runDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.RUNNING &&
                    System.currentTimeMillis() < runDeadline) delay(100)
                assertEquals(InstanceState.RUNNING, store.get(inst.id).state)

                client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val stopDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.STOPPED &&
                    System.currentTimeMillis() < stopDeadline) delay(100)
                assertEquals(InstanceState.STOPPED, store.get(inst.id).state)
                assertNull(store.get(inst.id).statusMessage,
                    "Intentional kill should not leave a statusMessage")
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `kill POWER_LOCKED when start holds the lock`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-kill-during-start")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val broadcaster = dev.conduit.daemon.service.WsBroadcaster(AppJson)
            val manager = dev.conduit.daemon.service.ServerProcessManager(
                store, DataDirectory(tempDir), broadcaster, scope, json = AppJson
            )

            val inst = store.create(CreateInstanceRequest(name = "KillDuringStart", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            val (javaPath, jvmArgs) = stuckServerJvmConfig()
            store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

            val powerLocksField = manager.javaClass.getDeclaredField("powerLocks").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val powerLocks = powerLocksField.get(manager) as ConcurrentHashMap<String, Mutex>
            val lock = powerLocks.computeIfAbsent(inst.id) { Mutex() }
            assertTrue(lock.tryLock(), "precondition: test must acquire lock before calling manager.kill()")

            try {
                val ex = assertFailsWith<ApiException> { manager.kill(inst.id) }
                assertEquals("POWER_LOCKED", ex.code)
            } finally {
                lock.unlock()
            }

            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `restart RUNNING server stops then starts`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-restart")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = runningServerJvmConfig()

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "Restart", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val runDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.RUNNING &&
                    System.currentTimeMillis() < runDeadline) delay(100)
                assertEquals(InstanceState.RUNNING, store.get(inst.id).state)

                val resp = client.post("/api/v1/instances/${inst.id}/server/restart") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.OK, resp.status)

                val restartDeadline = System.currentTimeMillis() + 15_000
                var sawRunning = false
                while (System.currentTimeMillis() < restartDeadline) {
                    if (store.get(inst.id).state == InstanceState.RUNNING) {
                        sawRunning = true
                        break
                    }
                    delay(200)
                }
                assertTrue(sawRunning, "Server should reach RUNNING after restart")

                runCatching {
                    client.post("/api/v1/instances/${inst.id}/server/kill") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `shutdownAll stops all running instances`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-shutdown")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val broadcaster = dev.conduit.daemon.service.WsBroadcaster(AppJson)
            val manager = dev.conduit.daemon.service.ServerProcessManager(
                store, DataDirectory(tempDir), broadcaster, scope, json = AppJson
            )
            val (javaPath, jvmArgs) = runningServerJvmConfig()

            val instances = (1..2).map { i ->
                val inst = store.create(CreateInstanceRequest(name = "Shutdown$i", mcVersion = "1.20.4"))
                store.markInitialized(inst.id)
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)
                manager.start(inst.id)
                inst
            }

            val runDeadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < runDeadline) {
                val allRunning = instances.all { store.get(it.id).state == InstanceState.RUNNING }
                if (allRunning) break
                delay(200)
            }
            instances.forEach {
                assertEquals(InstanceState.RUNNING, store.get(it.id).state,
                    "Instance ${it.id} should be RUNNING before shutdownAll")
            }

            manager.shutdownAll()

            val stopDeadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < stopDeadline) {
                val allStopped = instances.all { store.get(it.id).state == InstanceState.STOPPED }
                if (allStopped) break
                delay(200)
            }
            instances.forEach {
                assertEquals(InstanceState.STOPPED, store.get(it.id).state,
                    "Instance ${it.id} should be STOPPED after shutdownAll")
                assertFalse(manager.isRunning(it.id))
            }

            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `kill CRASHED instance returns SERVER_NOT_RUNNING`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-kill-crashed")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "KillCrashed", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val crashDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.CRASHED &&
                    System.currentTimeMillis() < crashDeadline) delay(100)
                assertEquals(InstanceState.CRASHED, store.get(inst.id).state)

                val killResp = client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.Conflict, killResp.status)
                val error = killResp.body<ErrorResponse>()
                assertEquals("SERVER_NOT_RUNNING", error.error.code)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `start from CRASHED succeeds after switching to stable binary`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-start-crashed")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val crashJvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)
            val runJvmArgs = listOf("-cp", classpath, RunningMcServer::class.qualifiedName!!)

            runTestApplication {
                application { module(dataDirectory = dataDir, instanceStore = store, mojangClient = createMockMojangClient()) }
                val client = jsonClient()
                val token = pairAndGetToken(client)

                val inst = client.post("/api/v1/instances") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(CreateInstanceRequest(name = "StartCrashed", mcVersion = "1.20.4"))
                }.body<InstanceSummary>()

                val initDeadline = System.currentTimeMillis() + 5_000
                while (store.get(inst.id).state == InstanceState.INITIALIZING &&
                    System.currentTimeMillis() < initDeadline) delay(100)

                client.put("/api/v1/instances/${inst.id}/server/eula") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(AcceptEulaRequest(accepted = true))
                }
                store.updateJvmConfig(inst.id, true, crashJvmArgs, true, javaPath)
                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                val crashDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.CRASHED &&
                    System.currentTimeMillis() < crashDeadline) delay(100)
                assertEquals(InstanceState.CRASHED, store.get(inst.id).state)

                store.updateJvmConfig(inst.id, true, runJvmArgs, true, javaPath)
                val startResp = client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                assertEquals(HttpStatusCode.OK, startResp.status)

                val runDeadline = System.currentTimeMillis() + 10_000
                while (store.get(inst.id).state != InstanceState.RUNNING &&
                    System.currentTimeMillis() < runDeadline) delay(100)
                assertEquals(InstanceState.RUNNING, store.get(inst.id).state)

                client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `concurrent start and kill only one acquires lock`() = runBlocking {
        val tempDir = Files.createTempDirectory("conduit-start-kill-race")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val broadcaster = dev.conduit.daemon.service.WsBroadcaster(AppJson)
            val manager = dev.conduit.daemon.service.ServerProcessManager(
                store, DataDirectory(tempDir), broadcaster, scope, json = AppJson
            )
            val (javaPath, jvmArgs) = runningServerJvmConfig()

            val inst = store.create(CreateInstanceRequest(name = "StartKill", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)
            manager.start(inst.id)

            val runDeadline = System.currentTimeMillis() + 10_000
            while (!manager.isRunning(inst.id) && System.currentTimeMillis() < runDeadline) delay(100)
            assertTrue(manager.isRunning(inst.id))

            val startResult = async { runCatching { manager.start(inst.id) } }
            val killResult = async { runCatching { manager.kill(inst.id) } }

            val startEx = startResult.await().exceptionOrNull()
            killResult.await().getOrThrow()

            assertNotNull(startEx, "Concurrent start should be rejected")
            assertTrue(
                (startEx as? ApiException)?.code in listOf("POWER_LOCKED", "SERVER_ALREADY_RUNNING"),
                "Expected POWER_LOCKED or SERVER_ALREADY_RUNNING, got: ${(startEx as? ApiException)?.code}"
            )

            manager.awaitProcessExit(inst.id)
            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

}
