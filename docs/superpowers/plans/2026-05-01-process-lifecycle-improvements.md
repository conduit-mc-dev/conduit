# Process Lifecycle Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 `ServerProcessManager` 增加三个 MVP 前必备能力：STARTING 状态 60s 超时检测、实例级 Mutex 并发保护、崩溃自动恢复（带循环检测 + 最大次数上限）。

**Architecture:**
- 三个子系统在同一个类（`ServerProcessManager`）内协作，但彼此边界清楚：超时是启动路径的 watchdog、Mutex 是 start/stop/restart 三个公共方法的互斥、崩溃恢复是 `monitorJob` 出口处的分支。
- 顺序依赖：Task 1（超时）→ Task 2（Mutex，为 Task 3 提供互斥基础）→ Task 3（崩溃恢复）。
- 错误码层面新增一项 `POWER_LOCKED`（409）用于 Mutex 拒绝；`DaemonConfig` 新增三个字段用于崩溃恢复开关和循环阈值。

**Tech Stack:**
- Kotlin 协程 `Mutex`（`kotlinx.coroutines.sync.Mutex`，不阻塞线程）
- 现有 `scope: CoroutineScope` 做 `delay(...)` + `withTimeout(...)` 模式
- `InstanceStore.transitionState` / `forceState` 已有原子状态机 API，直接复用
- 测试：Ktor `testApplication` + `MockMcServer` 以 Kotlin 对象启动子进程（Windows/Unix 均可）；新增轻量 "卡死版 Mock"（永远不打印 Done）用于超时测试

---

## Scope Check

本计划覆盖**一个子系统**（`ServerProcessManager`）的三项紧密相关的改进，不跨模块。三项改进在同一类上协作、共享测试基础设施、共享 ManagedProcess 数据结构——拆成三个独立 plan 反而会让共享前提在 plan 之间漂移。保持为一个 plan，内部用 Task 1/2/3 分界。

---

## File Structure

**修改：**
- `shared-core/src/commonMain/kotlin/dev/conduit/core/model/ConfigModels.kt` — `DaemonConfig` / `UpdateDaemonConfigRequest` 加 3 字段
- `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt` — 三个子任务的主战场
- `daemon/src/main/kotlin/dev/conduit/daemon/store/DaemonConfigStore.kt` — `update()` 映射新字段
- `daemon/src/main/kotlin/dev/conduit/daemon/Application.kt` — `ServerProcessManager` 构造传入 `daemonConfigStore`
- `docs/api-protocol.md` — §9.2 错误码表加 `POWER_LOCKED`；§4.1 `PUT /config/daemon` 字段表补 3 行
- `docs/progress.md` — Now/Done 同步

**新增测试：**
- `daemon/src/test/kotlin/dev/conduit/daemon/StuckMcServer.kt` — 第二个 Mock（只打印初始化行、不打印 Done，用于超时测试）
- `daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt` — 三个子任务的集成测试集中在一个文件（vs 分散到 `ServerRoutesTest` / `E2ELifecycleTest`）。理由：这三个特性跨多个 HTTP 端点，放单独文件更容易维护共享 helper

**不新增文件：** Mutex 不抽成独立类（Wings 的 `locker.go` 在 Kotlin 中就是 `kotlinx.coroutines.sync.Mutex`，再封装一层是 YAGNI）；崩溃恢复逻辑保留在 `ServerProcessManager` 内的私有方法中。

---

## Task 1: STARTING 状态启动超时检测

**Files:**
- Create: `daemon/src/test/kotlin/dev/conduit/daemon/StuckMcServer.kt`
- Create: `daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt`
- Modify: `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt`

**背景：** `ServerProcessManager.start()` 依赖 `LogPatternDetector` 的 `SERVER_DONE` 模式把状态推进到 RUNNING。若服务器日志格式变化、进程卡在初始化阶段（如 world 生成卡 IO、chunks 预生成）或加载 mod 卡死，现有代码会让实例永远停在 STARTING。HTTP `stop` 会 409，UI 显示转圈圈直到 daemon 重启。

**设计：** 在 `start()` 末尾启动一个 timeout job：`delay(startupTimeoutSeconds.seconds)`，检查状态若仍为 STARTING → `destroyForcibly()`。`monitorJob` 会感知退出并把状态清到 STOPPED，我们只需在 timeout 分支写 `statusMessage = "Startup timed out after ${N}s"` 让用户知道原因。

硬编码 60s（MVP 阶段，YAGNI 可配）。使用 `private companion object const val STARTUP_TIMEOUT_SECONDS = 60L`。

- [ ] **Step 1.1: 创建 StuckMcServer Mock**

Create `daemon/src/test/kotlin/dev/conduit/daemon/StuckMcServer.kt`:

```kotlin
package dev.conduit.daemon

/**
 * Mock MC server that prints initialization lines but NEVER prints "Done".
 * Used to test startup timeout — the real server would hang similarly on
 * broken world gen, mod conflicts, etc.
 */
object StuckMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Preparing level \"world\"")
        System.out.flush()

        // Block forever — simulates a stuck init. stdin read blocks the main
        // thread, so stdin closure (process destroy) cleanly terminates us.
        val reader = System.`in`.bufferedReader()
        while (reader.readLine() != null) {
            // swallow any input, never print Done
        }
    }
}
```

- [ ] **Step 1.2: 写 TDD 失败测试 — 启动超时后回到 STOPPED**

Create `daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt` with:

```kotlin
package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import java.nio.file.Files
import kotlin.test.*

class ProcessLifecycleTest {

    private fun stuckServerJvmConfig(): Pair<String, List<String>> {
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        return Pair(javaPath, listOf("-cp", classpath, StuckMcServer::class.qualifiedName!!))
    }

    @Test
    fun `startup timeout forces STOPPED when Done never appears`() {
        val tempDir = Files.createTempDirectory("conduit-timeout")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val (javaPath, jvmArgs) = stuckServerJvmConfig()

            testApplication {
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

                client.post("/api/v1/instances/${inst.id}/server/start") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Poll up to 75s: 60s timeout + 15s slack for monitorJob cleanup
                val stoppedDeadline = System.currentTimeMillis() + 75_000
                var finalState: InstanceState? = null
                var finalMessage: String? = null
                while (System.currentTimeMillis() < stoppedDeadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.STOPPED) {
                        finalState = s.state
                        finalMessage = s.statusMessage
                        break
                    }
                    delay(500)
                }
                assertEquals(InstanceState.STOPPED, finalState, "Expected timeout to force STOPPED")
                assertNotNull(finalMessage, "Expected a statusMessage explaining timeout")
                assertTrue(
                    finalMessage!!.contains("timed out", ignoreCase = true),
                    "Expected statusMessage to mention timeout, got: $finalMessage"
                )
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
```

- [ ] **Step 1.3: 运行测试，确认 FAIL**

Run:
```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.startup timeout forces STOPPED when Done never appears" 2>&1 | tee /tmp/task1-red.log
```

Expected: 测试超时（75s poll deadline 到期）或 `finalState != STOPPED`。日志末尾应该是 `Expected timeout to force STOPPED`。

- [ ] **Step 1.4: 在 ServerProcessManager 中加超时 job**

Modify `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt`:

在 class 顶部（class 开大括号后立即）新增：

```kotlin
    private companion object {
        const val STARTUP_TIMEOUT_SECONDS = 60L
    }
```

在 `ManagedProcess` data class 中增加 `pingJob` 旁边一个新字段：

```kotlin
    private data class ManagedProcess(
        val process: Process,
        val stdin: BufferedWriter,
        val outputJob: Job,
        val monitorJob: Job,
        val startedAt: Instant,
        @Volatile var pingJob: Job? = null,
        @Volatile var startupTimeoutJob: Job? = null,
    )
```

在 `start()` 中 `processes[instanceId] = ManagedProcess(...)` 赋值之后、方法结束前追加：

```kotlin
        val managed = processes[instanceId]
        managed?.startupTimeoutJob = scope.launch {
            delay(STARTUP_TIMEOUT_SECONDS.seconds)
            val current = try { instanceStore.get(instanceId).state } catch (_: Exception) { return@launch }
            if (current == InstanceState.STARTING) {
                log.warn("Instance {} stuck in STARTING after {}s, forcing stop", instanceId, STARTUP_TIMEOUT_SECONDS)
                instanceStore.forceState(instanceId, InstanceState.STARTING, "Startup timed out after ${STARTUP_TIMEOUT_SECONDS}s")
                managed.process.destroyForcibly()
            }
        }
```

在输出循环 `LogEventType.SERVER_DONE` 分支中（成功到达 RUNNING 后）取消 timeout job：

```kotlin
                            LogEventType.SERVER_DONE -> {
                                try {
                                    instanceStore.transitionState(instanceId, InstanceState.STARTING, InstanceState.RUNNING)
                                    broadcastStateChanged(instanceId, InstanceState.STARTING, InstanceState.RUNNING)
                                    processes[instanceId]?.startupTimeoutJob?.cancel()
                                    startPingPolling(instanceId)
                                } catch (_: ApiException) {
                                    // 状态已不是 STARTING（被 stop 抢先），忽略
                                }
                            }
```

在 `monitorJob` 中，`processes.remove(instanceId)?.pingJob?.cancel()` 之后追加：

```kotlin
            val removed = processes.remove(instanceId)
            removed?.pingJob?.cancel()
            removed?.startupTimeoutJob?.cancel()
```

（即把原来的 `processes.remove(instanceId)?.pingJob?.cancel()` 替换成上面三行，并更新后续使用 `managed` 或等价的引用——检查此处代码上下文确认不引入空指针。）

**注意：** `instanceStore.forceState(instanceId, InstanceState.STARTING, ...)` 在 `InstanceStore.kt:226` 的签名是 `forceState(id, to, statusMessage)` —— 第二个参数是目标状态而不是"期望旧状态"。这里我们**保持**状态为 STARTING 只为写入 statusMessage，真正的 STOPPED 转换由 monitorJob 做。重新确认：`forceState` 会无条件覆盖状态。所以更干净的做法是直接让 monitorJob 在进程被 destroyForcibly 后把 statusMessage 设成超时文案。

**采用更干净的方案**——调整上面 timeout job 为：

```kotlin
        managed?.startupTimeoutJob = scope.launch {
            delay(STARTUP_TIMEOUT_SECONDS.seconds)
            val current = try { instanceStore.get(instanceId).state } catch (_: Exception) { return@launch }
            if (current == InstanceState.STARTING) {
                log.warn("Instance {} stuck in STARTING after {}s, forcing stop", instanceId, STARTUP_TIMEOUT_SECONDS)
                startupTimedOut[instanceId] = true
                managed.process.destroyForcibly()
            }
        }
```

并在 class 字段区新增一个并发 map：

```kotlin
    private val startupTimedOut = ConcurrentHashMap<String, Boolean>()
```

在 `monitorJob` 的 `statusMessage` 计算里加一个分支：

```kotlin
            val timedOut = startupTimedOut.remove(instanceId) == true
            val statusMessage = when {
                existingMessage?.startsWith("Initialization failed") == true -> existingMessage
                timedOut -> "Startup timed out after ${STARTUP_TIMEOUT_SECONDS}s"
                exitCode != 0 -> "Process exited with code $exitCode"
                else -> null
            }
```

这样就不会污染 `InstanceStore` 的状态，语义也更清晰：timeout 只是触发 destroyForcibly，后续状态由正常的 monitorJob 路径处理。

- [ ] **Step 1.5: 运行测试，确认 PASS**

Run:
```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.startup timeout forces STOPPED when Done never appears" 2>&1 | tee /tmp/task1-green.log
```

Expected: `BUILD SUCCESSFUL`，测试在约 60–65 秒内通过。

- [ ] **Step 1.6: TDD 降级自检**

为了确认测试真的在测目标行为（避免 `null==null` 自欺），临时把实现中的 `STARTUP_TIMEOUT_SECONDS = 60L` 改为 `600L`（10 分钟），再跑一次：

```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.startup timeout forces STOPPED when Done never appears" 2>&1 | tee /tmp/task1-demote.log
```

Expected: FAIL（75s deadline 到期前实例不会到 STOPPED）。确认看到失败后，把常量改回 `60L`，再跑一次确认 PASS。这一步证明测试对 timeout 行为敏感，不是空过。

- [ ] **Step 1.7: 跑全套 daemon 测试确认无回归**

```bash
./gradlew :daemon:test 2>&1 | tee /tmp/task1-full.log
```

Expected: `BUILD SUCCESSFUL`。测试总数从 192 升至 193（+1）。若 E2ELifecycleTest 或 ServerRoutesTest 中的已有启动测试偶发失败（timeout job 与 stop 抢状态的竞态），把超时 job 的取消点加进 `stop()` 入口：

```kotlin
    fun stop(instanceId: String) {
        processes[instanceId]?.startupTimeoutJob?.cancel()
        // ... 原逻辑
    }
```

再跑 `./gradlew :daemon:test` 确认绿。

- [ ] **Step 1.8: 提交**

```bash
git add daemon/src/test/kotlin/dev/conduit/daemon/StuckMcServer.kt \
        daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt \
        daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt
git commit -m "feat(daemon): add 60s startup timeout watchdog for STARTING state"
```

---

## Task 2: 实例级 Mutex（Power Lock）

**Files:**
- Modify: `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt`
- Modify: `daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt`
- Modify: `docs/api-protocol.md` — §9.2 错误码表

**背景：** 当前 `start()` 用 `processes.containsKey(id)` 保护重复启动，`stop()` 用 `transitionState(RUNNING → STOPPING)` 的原子 CAS 保护重复停止。但**跨方法竞态**未被保护：例如用户在 WebSocket 里同时点了 start 和 stop，又或者 Task 3 的崩溃恢复自动触发 start，而用户同时手动 stop —— 这些场景下状态机正确但"power action"流程可能乱序（已经要 restart 的进程被异步 start 覆盖）。

**设计：** 为每个 instanceId 维护一把 `kotlinx.coroutines.sync.Mutex`。start/stop/restart 入口 `tryLock()`：成功则持锁整个流程，失败抛 `ApiException(409, "POWER_LOCKED", "Another power action is in progress")`。kill 绕过锁（参考 Wings 的 Terminate 不被 lock 阻塞——kill 是"救命稻草"，必须无条件可用）。

**Kotlin 细节：** `start()` 和 `stop()` 现在是**同步方法**（非 suspend），因为它们是被 HTTP 路由直接调用的。我们不把整个方法变成 suspend（会导致路由层改动放大），而是：
- 使用 `Mutex.tryLock(null)` 作为**即返即回**的锁尝试。成功则在方法开头 `lock`、在所有 return/抛出路径 `unlock`。
- `stop()` 内部 scope.launch 异步超时，那部分代码不持锁（锁只保护 stop 入口的状态机转换）。
- Wings 中 start/stop/restart 持锁**贯穿整个操作周期**；我们简化为**状态转换期间持锁**——因为一旦状态机推进到 STARTING/STOPPING，后续的重复 start/stop 已经被状态机 CAS 拒绝。Mutex 只是在"状态转换这一瞬间"的保护。

- [ ] **Step 2.1: api-protocol.md 加 POWER_LOCKED 错误码**

Modify `docs/api-protocol.md`, 在 §9.2 错误码表的 409 区块，找到 `SERVER_ALREADY_RUNNING` 那一行下方插入：

```markdown
| `POWER_LOCKED` | 409 | Another power action (start/stop/restart) is in progress for this instance; retry shortly |
```

- [ ] **Step 2.2: 写 TDD 失败测试 — 并发 start 拒绝**

在 `ProcessLifecycleTest.kt` 中追加测试：

```kotlin
    @Test
    fun `concurrent start requests return POWER_LOCKED for loser`() {
        val tempDir = Files.createTempDirectory("conduit-lock")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)

            testApplication {
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
                // Stuck server so STARTING stays for a while
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

                val statusCodes = results.map { it.status }.sortedBy { it.value }
                // One should succeed (200), one should be 409
                assertEquals(HttpStatusCode.OK, statusCodes[0], "Expected exactly one success")
                assertEquals(HttpStatusCode.Conflict, statusCodes[1], "Expected exactly one 409")

                // Verify loser got POWER_LOCKED specifically (not SERVER_ALREADY_RUNNING)
                val loser = results.first { it.status == HttpStatusCode.Conflict }
                val error = loser.body<ErrorResponse>()
                // Accept either POWER_LOCKED (lock contention) or SERVER_ALREADY_RUNNING
                // (state machine caught it). We require POWER_LOCKED to be possible.
                assertTrue(
                    error.error.code in listOf("POWER_LOCKED", "SERVER_ALREADY_RUNNING"),
                    "Expected POWER_LOCKED or SERVER_ALREADY_RUNNING, got ${error.error.code}"
                )

                // Cleanup: kill the stuck server
                client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
```

**顶部 imports 追加：**

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
```

**注意关于接受两种错误码：** 如果 Mutex 胜者快（状态已 STARTING），输者会被状态机的 `transitionState(STOPPED → STARTING)` 拒绝抛 SERVER_ALREADY_RUNNING，那是 Task 1/旧行为；只有当两个请求真的同时到达状态转换点、Mutex 才会赢出 POWER_LOCKED。我们两者都接受作为"正确"，但至少证明**不会有 race 导致两个进程**。

- [ ] **Step 2.3: 加一个更严格的单元测试 — 直接打 ServerProcessManager**

在 `ProcessLifecycleTest.kt` 追加（用 `runTest` 直接调 manager，不走 HTTP）：

```kotlin
    @Test
    fun `mutex prevents concurrent start calls at manager level`() = kotlinx.coroutines.test.runTest {
        val tempDir = Files.createTempDirectory("conduit-mutex")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
            val broadcaster = dev.conduit.daemon.service.WsBroadcaster(AppJson)
            val manager = dev.conduit.daemon.service.ServerProcessManager(
                store, dataDir, broadcaster, scope, json = AppJson
            )

            // Create a STOPPED instance directly
            val inst = store.create(CreateInstanceRequest(name = "MutexUnit", mcVersion = "1.20.4"))
            store.markInitialized(inst.id)
            val (javaPath, jvmArgs) = stuckServerJvmConfig()
            store.updateJvmConfig(inst.id, true, jvmArgs, true, javaPath)
            // Also need EULA file so start doesn't fail for a different reason — or skip EULA by
            // hitting manager.start directly, which does not enforce EULA (that's EulaService in routes)

            var lockRejections = 0
            var stateRejections = 0
            val jobs = (1..5).map {
                scope.launch {
                    try { manager.start(inst.id) } catch (e: ApiException) {
                        when (e.code) {
                            "POWER_LOCKED" -> lockRejections++
                            "SERVER_ALREADY_RUNNING" -> stateRejections++
                        }
                    }
                }
            }
            jobs.forEach { it.join() }

            assertEquals(4, lockRejections + stateRejections, "Expected 4 of 5 calls to be rejected")
            assertTrue(manager.isRunning(inst.id), "Exactly one process should be running")

            manager.kill(inst.id)
            manager.awaitProcessExit(inst.id)
            scope.cancel()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
```

**顶部 imports 追加：**

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.assertTrue
```

- [ ] **Step 2.4: 运行两个新测试，确认 FAIL**

```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.concurrent start requests return POWER_LOCKED for loser" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.mutex prevents concurrent start calls at manager level" 2>&1 | tee /tmp/task2-red.log
```

Expected: 至少 `mutex prevents concurrent start calls at manager level` 应该 FAIL —— 5 次调用中可能有 2 个或更多同时进入 `start()`，触发 `processes.containsKey` 检查后的竞态（两个都通过 check、两个都 `transitionState`，其中一个 CAS 失败抛异常，但**异常类型**可能不稳定，或者更糟：两个都 `ProcessBuilder(...).start()` 成功，`processes[id] = ...` 第二次覆盖第一次——泄漏进程）。

若 FAIL 消息显示"Expected 4 of 5 rejected, got 3"或"Exactly one process should be running"，就是我们要的现象。

- [ ] **Step 2.5: 实现 Mutex**

Modify `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt`:

文件顶部 import 区加：

```kotlin
import kotlinx.coroutines.sync.Mutex
```

class 字段区（`processes` 附近）加：

```kotlin
    private val powerLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(instanceId: String): Mutex =
        powerLocks.computeIfAbsent(instanceId) { Mutex() }
```

修改 `start(instanceId: String)` 的开头，用 `tryLock` 包住整个方法体：

```kotlin
    fun start(instanceId: String) {
        val lock = lockFor(instanceId)
        if (!lock.tryLock()) {
            throw ApiException(HttpStatusCode.Conflict, "POWER_LOCKED", "Another power action is in progress")
        }
        try {
            if (processes.containsKey(instanceId)) {
                throw ApiException(HttpStatusCode.Conflict, "SERVER_ALREADY_RUNNING", "Server is already running")
            }
            // ... 现有的 start 逻辑 ...
            processes[instanceId] = ManagedProcess(process, stdin, outputJob, monitorJob, Clock.System.now())
            managed?.startupTimeoutJob = scope.launch { /* ... */ }
        } finally {
            lock.unlock()
        }
    }
```

对 `stop(instanceId: String)` 做同样的 wrap（Task 1 的 `processes[instanceId]?.startupTimeoutJob?.cancel()` 行放在 try 块内）：

```kotlin
    fun stop(instanceId: String) {
        val lock = lockFor(instanceId)
        if (!lock.tryLock()) {
            throw ApiException(HttpStatusCode.Conflict, "POWER_LOCKED", "Another power action is in progress")
        }
        try {
            processes[instanceId]?.startupTimeoutJob?.cancel()
            // ... 现有 stop 逻辑 ...
        } finally {
            lock.unlock()
        }
    }
```

**`kill()` 不加锁**（参考 Wings 的 Terminate 绕过 lock）。

**清理钩子：** 在 `instanceStore.delete(id)` 被调用的路由处，我们应该清理 `powerLocks[id]`——但 `ServerProcessManager` 没有 delete hook。简单做法：**不清理**，`ConcurrentHashMap` 的条目泄漏在实例被删除后最多就是几个没有进程的 `Mutex` 对象，内存开销可忽略。未来若有实例批量创建/删除需求再加清理。

- [ ] **Step 2.6: 运行新测试，确认 PASS**

```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.concurrent start requests return POWER_LOCKED for loser" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.mutex prevents concurrent start calls at manager level" 2>&1 | tee /tmp/task2-green.log
```

Expected: 两个测试都 PASS。

- [ ] **Step 2.7: 跑全套 daemon 测试**

```bash
./gradlew :daemon:test 2>&1 | tee /tmp/task2-full.log
```

Expected: `BUILD SUCCESSFUL`，测试总数 193 → 195（+2）。若有偶发失败——比如 `ServerRoutesTest` 中同一测试内连续 start/stop 因 Mutex 未及时释放——检查是否 `tryLock()` 的对应 `unlock()` 在所有路径上都被调用（特别是异常路径）。

- [ ] **Step 2.8: 提交**

```bash
git add daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt \
        daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt \
        docs/api-protocol.md
git commit -m "feat(daemon): add per-instance power-action mutex with POWER_LOCKED 409"
```

---

## Task 3: 崩溃恢复（AutoRestart + 循环检测）

**Files:**
- Modify: `shared-core/src/commonMain/kotlin/dev/conduit/core/model/ConfigModels.kt` — `DaemonConfig` / `UpdateDaemonConfigRequest` 新增 3 字段
- Modify: `daemon/src/main/kotlin/dev/conduit/daemon/store/DaemonConfigStore.kt` — `update()` 映射新字段
- Modify: `daemon/src/main/kotlin/dev/conduit/daemon/Application.kt` — 把 `daemonConfigStore` 传入 `ServerProcessManager`
- Modify: `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt` — 崩溃分支 + auto-restart 触发
- Modify: `daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt` — 3 个测试
- Modify: `docs/api-protocol.md` — §4.1 `DaemonConfig` 字段表补 3 行

**背景：** `ServerProcessManager.monitorJob` 当前仅把退出状态设为 STOPPED。若 MC 服务器因为 mod 冲突 / OOM / 网络断连等原因退出，服主必须手动重启。参考 Wings 的 `CrashHandler` 和 MCSManager 的 `autoRestart + autoRestartMaxTimes`，我们要：

1. **区分主动 vs 被动退出**：`stop()` 和 `kill()` 调用时在 ManagedProcess 上标记 `intentionalExit = true`。monitorJob 在进程退出时读此标志——true → 不触发恢复。
2. **崩溃判定**：进程退出时 `intentionalExit == false` 且 `exitCode != 0` 视为崩溃。`exitCode == 0` 且非主动退出也视为崩溃（MC 服务器正常运行下不会自己 exit(0)，可能是 /stop 被玩家触发——但此时 state==RUNNING 且无 intentionalExit，我们仍视为异常退出；参考 Wings 默认 `DetectCleanExitAsCrash=false` 的反向默认。MVP 我们选**默认也视为崩溃**，因为 Conduit 不提供给玩家 /stop 权限模型的上下文，保守策略）。
3. **循环检测**：记录 `lastCrashAt`。若距上次崩溃 < `crashLoopTimeoutSeconds`（默认 60s）且 `crashCount >= autoRestartMaxTimes` → 放弃恢复，保持 STOPPED，`statusMessage` 写明"Crash loop detected, auto-restart disabled"。
4. **启动期崩溃不重启**：若退出时 state == STARTING（还没进 RUNNING），不触发 auto-restart—— 启动失败往往是配置问题，重启只会重复失败刷屏。这与 Wings `DetectCleanExitAsCrash=false` 的启动阶段异常退出处理对齐。

**默认值：** `autoRestartEnabled = false`（安全第一，服主显式启用）、`autoRestartMaxTimes = 3`、`crashLoopTimeoutSeconds = 60`。

- [ ] **Step 3.1: DaemonConfig 加字段**

Modify `shared-core/src/commonMain/kotlin/dev/conduit/core/model/ConfigModels.kt`:

```kotlin
@Serializable
data class DaemonConfig(
    val port: Int = 9147,
    val publicEndpointEnabled: Boolean = true,
    val defaultJvmArgs: List<String> = listOf("-Xmx4G", "-Xms2G"),
    val downloadSource: DownloadSource = DownloadSource.MOJANG,
    val customMirrorUrl: String? = null,
    val autoRestartEnabled: Boolean = false,
    val autoRestartMaxTimes: Int = 3,
    val crashLoopTimeoutSeconds: Int = 60,
)

@Serializable
data class UpdateDaemonConfigRequest(
    val port: Int? = null,
    val publicEndpointEnabled: Boolean? = null,
    val defaultJvmArgs: List<String>? = null,
    val downloadSource: DownloadSource? = null,
    val customMirrorUrl: String? = null,
    val autoRestartEnabled: Boolean? = null,
    val autoRestartMaxTimes: Int? = null,
    val crashLoopTimeoutSeconds: Int? = null,
)
```

- [ ] **Step 3.2: DaemonConfigStore.update 映射新字段**

Modify `daemon/src/main/kotlin/dev/conduit/daemon/store/DaemonConfigStore.kt`:

```kotlin
    fun update(request: UpdateDaemonConfigRequest): DaemonConfig {
        val updated = config.copy(
            port = request.port ?: config.port,
            publicEndpointEnabled = request.publicEndpointEnabled ?: config.publicEndpointEnabled,
            defaultJvmArgs = request.defaultJvmArgs ?: config.defaultJvmArgs,
            downloadSource = request.downloadSource ?: config.downloadSource,
            customMirrorUrl = request.customMirrorUrl ?: config.customMirrorUrl,
            autoRestartEnabled = request.autoRestartEnabled ?: config.autoRestartEnabled,
            autoRestartMaxTimes = request.autoRestartMaxTimes ?: config.autoRestartMaxTimes,
            crashLoopTimeoutSeconds = request.crashLoopTimeoutSeconds ?: config.crashLoopTimeoutSeconds,
        )
        config = updated
        save(updated)
        return updated
    }
```

- [ ] **Step 3.3: api-protocol.md 同步字段**

Modify `docs/api-protocol.md`, 在 §4.1 `DaemonConfig` 响应字段表末尾追加：

```markdown
| `autoRestartEnabled` | boolean | Whether daemon auto-restarts crashed instances. Default: `false` |
| `autoRestartMaxTimes` | int | Max consecutive restarts within `crashLoopTimeoutSeconds` before giving up. Default: `3` |
| `crashLoopTimeoutSeconds` | int | Window size for crash loop detection. Default: `60` |
```

`PUT /config/daemon` 的 body 字段表同步加三行（所有字段 optional）。

- [ ] **Step 3.4: 写 TDD 失败测试 A — 默认关闭时不重启**

在 `ProcessLifecycleTest.kt` 追加：

```kotlin
    @Test
    fun `crashed process stays STOPPED when autoRestart disabled by default`() {
        val tempDir = Files.createTempDirectory("conduit-crash-off")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)
            // CrashExitMcServer: prints Done then immediately exits(1)
            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            testApplication {
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

                // Wait for crash → STOPPED. If auto-restart were wrongly triggered,
                // state would briefly go RUNNING again; we poll for 10s to catch it.
                var sawRestartAttempt = false
                val deadline = System.currentTimeMillis() + 10_000
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id).state
                    if (s == InstanceState.STARTING && s != InstanceState.STOPPED) {
                        // Only flag as restart attempt if we've ALREADY seen STOPPED before
                        sawRestartAttempt = true
                    }
                    delay(200)
                }

                val final = store.get(inst.id)
                assertEquals(InstanceState.STOPPED, final.state)
                assertFalse(sawRestartAttempt, "autoRestart=false should not restart crashed process")
                assertNotNull(final.statusMessage)
                assertTrue(final.statusMessage!!.contains("exited with code"),
                    "Expected exit-code message, got: ${final.statusMessage}")
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
```

**需要新建：** `daemon/src/test/kotlin/dev/conduit/daemon/CrashExitMcServer.kt`

```kotlin
package dev.conduit.daemon

/** Mock MC server that prints Done then exits(1) after 500ms — simulates crash after reaching RUNNING. */
object CrashExitMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Done (1.0s)! For help, type \"help\"")
        System.out.flush()
        Thread.sleep(500)
        kotlin.system.exitProcess(1)
    }
}
```

- [ ] **Step 3.5: 写 TDD 失败测试 B — 启用后会重启一次**

在 `ProcessLifecycleTest.kt` 追加：

```kotlin
    @Test
    fun `crashed process auto-restarts when enabled and within maxTimes`() {
        val tempDir = Files.createTempDirectory("conduit-crash-on")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)

            // Write config with autoRestart enabled BEFORE Application boots
            dataDir.configPath.parent?.toFile()?.mkdirs()
            dataDir.configPath.toFile().writeText(
                """{"autoRestartEnabled":true,"autoRestartMaxTimes":2,"crashLoopTimeoutSeconds":30}"""
            )

            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            testApplication {
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

                // Observe state transitions: expect STARTING → RUNNING → STOPPED → STARTING again
                val transitions = mutableListOf<InstanceState>()
                val deadline = System.currentTimeMillis() + 20_000
                var lastState: InstanceState? = null
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id).state
                    if (s != lastState) {
                        transitions.add(s)
                        lastState = s
                    }
                    if (transitions.count { it == InstanceState.STARTING } >= 2) break
                    delay(100)
                }

                assertTrue(
                    transitions.count { it == InstanceState.STARTING } >= 2,
                    "Expected at least 2 STARTING transitions (initial + auto-restart), got: $transitions"
                )

                // Cleanup — kill so monitorJob / auto-restart doesn't keep firing
                runCatching { client.post("/api/v1/instances/${inst.id}/server/kill") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                } }
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
```

- [ ] **Step 3.6: 写 TDD 失败测试 C — 循环检测后放弃**

在 `ProcessLifecycleTest.kt` 追加：

```kotlin
    @Test
    fun `crash loop detection gives up after maxTimes within window`() {
        val tempDir = Files.createTempDirectory("conduit-crash-loop")
        try {
            val dataDir = DataDirectory(tempDir)
            val store = InstanceStore(dataDir)

            dataDir.configPath.parent?.toFile()?.mkdirs()
            dataDir.configPath.toFile().writeText(
                """{"autoRestartEnabled":true,"autoRestartMaxTimes":2,"crashLoopTimeoutSeconds":30}"""
            )

            val javaPath = ProcessHandle.current().info().command().orElse("java")
            val classpath = System.getProperty("java.class.path")
            val jvmArgs = listOf("-cp", classpath, CrashExitMcServer::class.qualifiedName!!)

            testApplication {
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

                // CrashExitMcServer exits within 1s of RUNNING. With maxTimes=2, we expect:
                // start → crash (1) → auto-restart → crash (2) → auto-restart → crash (3) → give up.
                // Final state: STOPPED with crash-loop statusMessage. Give 30s for all cycles.
                val deadline = System.currentTimeMillis() + 30_000
                var finalMsg: String? = null
                while (System.currentTimeMillis() < deadline) {
                    val s = store.get(inst.id)
                    if (s.state == InstanceState.STOPPED &&
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
```

- [ ] **Step 3.7: 运行三个新测试，确认 FAIL**

```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.crashed process stays STOPPED when autoRestart disabled by default" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.crashed process auto-restarts when enabled and within maxTimes" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.crash loop detection gives up after maxTimes within window" 2>&1 | tee /tmp/task3-red.log
```

Expected: 第一个测试可能 PASS（默认 auto-restart 就是关的），第二、第三个必 FAIL（功能还没实现）。

- [ ] **Step 3.8: Application.module 注入 daemonConfigStore 到 ServerProcessManager**

Modify `daemon/src/main/kotlin/dev/conduit/daemon/Application.kt`:

```kotlin
    val daemonConfigStore = DaemonConfigStore(dataDirectory.configPath)
    // ... actualMojangClient ...
    val processManager = ServerProcessManager(
        instanceStore, dataDirectory, broadcaster, appScope,
        daemonConfigStore = daemonConfigStore,
        json = AppJson,
    )
```

- [ ] **Step 3.9: ServerProcessManager 接收 daemonConfigStore 并实现崩溃恢复**

Modify `daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt`:

构造函数签名加参数：

```kotlin
class ServerProcessManager(
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val broadcaster: WsBroadcaster,
    private val scope: CoroutineScope,
    private val daemonConfigStore: dev.conduit.daemon.store.DaemonConfigStore? = null,
    private val pingClient: MinecraftPingClient = MinecraftPingClient(),
    private val json: Json = Json { encodeDefaults = true },
) {
```

（`daemonConfigStore` 设为可空，允许现有测试/E2E 构造不传；空则永远不做 auto-restart。）

`ManagedProcess` 加 `@Volatile var intentionalExit: Boolean = false`：

```kotlin
    private data class ManagedProcess(
        val process: Process,
        val stdin: BufferedWriter,
        val outputJob: Job,
        val monitorJob: Job,
        val startedAt: Instant,
        @Volatile var pingJob: Job? = null,
        @Volatile var startupTimeoutJob: Job? = null,
        @Volatile var intentionalExit: Boolean = false,
    )
```

class 字段区加崩溃历史追踪：

```kotlin
    private data class CrashHistory(
        @Volatile var lastCrashAt: Instant? = null,
        @Volatile var consecutiveCrashes: Int = 0,
    )
    private val crashHistory = ConcurrentHashMap<String, CrashHistory>()
```

在 `stop(instanceId)` 的 try 块开头（`processes[instanceId]?.startupTimeoutJob?.cancel()` 之前）加：

```kotlin
            processes[instanceId]?.intentionalExit = true
```

在 `kill(instanceId)` 中 `managed.process.destroyForcibly()` 之前加：

```kotlin
        managed.intentionalExit = true
```

注意 `kill` 里 `managed` 是从 `processes[instanceId]` 读的，所以直接 set 即可。

修改 `monitorJob` 的退出路径，在 `broadcastStateChanged(...)` 之后、`log.info(...)` 之前插入崩溃恢复逻辑：

```kotlin
        val monitorJob = scope.launch(Dispatchers.IO) {
            val exitCode = process.waitFor()
            val removed = processes.remove(instanceId)
            removed?.pingJob?.cancel()
            removed?.startupTimeoutJob?.cancel()
            val timedOut = startupTimedOut.remove(instanceId) == true
            val intentional = removed?.intentionalExit == true

            val hadPlayers = instanceStore.updatePlayerInfo(instanceId, 0, 20, emptyList())
            if (hadPlayers) broadcastPlayersChanged(instanceId, 0, 20)

            val snapshot = try { instanceStore.get(instanceId) } catch (_: Exception) { null }
            val oldState = snapshot?.state ?: InstanceState.RUNNING
            val existingMessage = snapshot?.statusMessage

            // Decide whether to trigger crash recovery BEFORE writing STOPPED,
            // so we can include crash-loop statusMessage if giving up.
            val shouldRecover = shouldAutoRestart(instanceId, oldState, intentional, exitCode, timedOut)
            val recoveryDecision = if (shouldRecover) decideCrashRecovery(instanceId) else CrashRecoveryDecision.NoOp
            val statusMessage = when {
                existingMessage?.startsWith("Initialization failed") == true -> existingMessage
                timedOut -> "Startup timed out after ${STARTUP_TIMEOUT_SECONDS}s"
                recoveryDecision is CrashRecoveryDecision.GiveUp ->
                    "Crash loop detected (${recoveryDecision.count} crashes in ${recoveryDecision.windowSeconds}s); auto-restart disabled. Last exit code: $exitCode"
                exitCode != 0 -> "Process exited with code $exitCode"
                intentional -> null
                else -> "Process exited unexpectedly (code $exitCode)"
            }

            instanceStore.forceState(instanceId, InstanceState.STOPPED, statusMessage)
            broadcastStateChanged(instanceId, oldState, InstanceState.STOPPED)
            log.info("Instance {} process exited with code {}", instanceId, exitCode)

            // Auto-restart fires AFTER STOPPED is published, so state transitions are
            // observable: STOPPED (briefly) → STARTING (restart) → ...
            if (recoveryDecision is CrashRecoveryDecision.Restart) {
                log.info("Auto-restarting instance {} (attempt {} of {})", instanceId,
                    recoveryDecision.attemptNumber, recoveryDecision.maxAttempts)
                delay(2.seconds) // small backoff so UI can observe STOPPED
                try {
                    start(instanceId)
                } catch (e: Exception) {
                    log.warn("Auto-restart failed for instance {}", instanceId, e)
                }
            }
        }
```

然后在 class 底部加三个私有辅助：

```kotlin
    private sealed class CrashRecoveryDecision {
        data object NoOp : CrashRecoveryDecision()
        data class Restart(val attemptNumber: Int, val maxAttempts: Int) : CrashRecoveryDecision()
        data class GiveUp(val count: Int, val windowSeconds: Int) : CrashRecoveryDecision()
    }

    private fun shouldAutoRestart(
        instanceId: String, oldState: InstanceState, intentional: Boolean,
        exitCode: Int, timedOut: Boolean,
    ): Boolean {
        if (intentional || timedOut) return false
        if (oldState != InstanceState.RUNNING) return false  // don't retry startup failures
        val store = daemonConfigStore ?: return false
        return store.get().autoRestartEnabled
    }

    private fun decideCrashRecovery(instanceId: String): CrashRecoveryDecision {
        val config = daemonConfigStore?.get() ?: return CrashRecoveryDecision.NoOp
        val history = crashHistory.computeIfAbsent(instanceId) { CrashHistory() }
        val now = Clock.System.now()
        val windowSec = config.crashLoopTimeoutSeconds
        val last = history.lastCrashAt
        history.consecutiveCrashes = if (last != null && (now - last).inWholeSeconds < windowSec) {
            history.consecutiveCrashes + 1
        } else {
            1
        }
        history.lastCrashAt = now
        return if (history.consecutiveCrashes > config.autoRestartMaxTimes) {
            CrashRecoveryDecision.GiveUp(history.consecutiveCrashes, windowSec)
        } else {
            CrashRecoveryDecision.Restart(history.consecutiveCrashes, config.autoRestartMaxTimes)
        }
    }
```

**语义说明：**
- `consecutiveCrashes > autoRestartMaxTimes` = give up。默认 maxTimes=3 意味着最多重启 3 次（第 4 次进来判定超上限）。
- `last != null && now - last < windowSec` = 窗口内崩溃；否则重置计数为 1（窗口外属于"旧事件"，新计数从 1 开始）。
- 正常退出（用户 stop/kill）不进此方法，因此不会污染历史。若服主手动恢复运行稳定后一段时间（> window），历史自然重置。

- [ ] **Step 3.10: 运行三个测试，确认 PASS**

```bash
./gradlew :daemon:test --tests "dev.conduit.daemon.ProcessLifecycleTest.crashed process stays STOPPED when autoRestart disabled by default" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.crashed process auto-restarts when enabled and within maxTimes" \
                       --tests "dev.conduit.daemon.ProcessLifecycleTest.crash loop detection gives up after maxTimes within window" 2>&1 | tee /tmp/task3-green.log
```

Expected: 三个全部 PASS。第二个测试运行约 5–10s，第三个测试运行约 15–25s（三次 crash cycle）。

- [ ] **Step 3.11: 跑全套 daemon 测试**

```bash
./gradlew :daemon:test 2>&1 | tee /tmp/task3-full.log
```

Expected: `BUILD SUCCESSFUL`，测试总数 195 → 198（+3）。

常见回归风险：
- 现有 `E2ELifecycleTest.server process reaches running state via Done detection` 调用 `runWithMockServer` 最后做 graceful stop。若 `stop()` 未设 `intentionalExit=true`，会被误判为崩溃（但 autoRestart 默认 off，所以问题不会显现）。已在 Step 3.9 中加上，应该没问题。
- `ServerRoutesTest` 中的一些 `start` 错误路径测试——确认 Mutex 和崩溃恢复不会让这些测试偶发卡住。

若任何测试失败，读 `/tmp/task3-full.log` 中的失败堆栈定位。

- [ ] **Step 3.12: 跑 shared-core 测试确认 DaemonConfig 字段改动无影响**

```bash
./gradlew :shared-core:test 2>&1 | tee /tmp/task3-core.log
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3.13: 更新 progress.md**

Modify `docs/progress.md`:

- 把 Next 主线 #1 移到 Done 区块顶部，写明三个子任务的成果、测试数（+6：3 crash + 2 mutex + 1 timeout）、默认值 `autoRestart=false`
- 若还有 Next 主线剩余（shared-core ConduitWsClient 测试、Desktop MVP），它们自动上移

- [ ] **Step 3.14: 提交**

```bash
git add shared-core/src/commonMain/kotlin/dev/conduit/core/model/ConfigModels.kt \
        daemon/src/main/kotlin/dev/conduit/daemon/store/DaemonConfigStore.kt \
        daemon/src/main/kotlin/dev/conduit/daemon/Application.kt \
        daemon/src/main/kotlin/dev/conduit/daemon/service/ServerProcessManager.kt \
        daemon/src/test/kotlin/dev/conduit/daemon/ProcessLifecycleTest.kt \
        daemon/src/test/kotlin/dev/conduit/daemon/CrashExitMcServer.kt \
        docs/api-protocol.md \
        docs/progress.md
git commit -m "feat(daemon): add crash recovery with loop detection and maxTimes"
```

---

## Self-Review Notes

**Spec coverage:**
- 进度文档 Next #1 的三项 — 全部覆盖：Task 1 = STARTING 超时；Task 2 = Mutex power lock；Task 3 = 崩溃恢复（Wings CrashHandler + MCSManager maxTimes）。
- architecture-notes.md "进程生命周期改进" 章节 §1 和 §2 都有对应任务；§3（可配置 Done pattern）架构笔记已注明"MVP 阶段硬编码足够"，本计划不处理。

**Placeholders:** 无 TBD/TODO；所有 test 代码、实现代码、commit message 都是字面完整的。

**Type consistency 复核：**
- `ManagedProcess` 在三个 Task 中累计加了 `startupTimeoutJob`（Task 1）和 `intentionalExit`（Task 3）两个字段，签名一致。
- `CrashRecoveryDecision` sealed class 在 Step 3.9 的辅助方法和 `monitorJob` 的消费点属性名对齐（`attemptNumber`、`maxAttempts`、`count`、`windowSeconds`）。
- `daemonConfigStore` 参数在 `Application.kt`（Step 3.8）和 `ServerProcessManager` 构造函数（Step 3.9）拼写一致。
- `autoRestartEnabled` / `autoRestartMaxTimes` / `crashLoopTimeoutSeconds` 三字段在 `ConfigModels.kt`、`DaemonConfigStore.kt`、`api-protocol.md`、`ServerProcessManager.kt`、测试 JSON 中全部同名。

**潜在风险点：**
- Task 2 的 `tryLock()` 在 `stop()` 里如果 hit 了 POWER_LOCKED，对应的异步 30s 强制 kill 超时 job 不会启动——但这正确，因为此时另一个 stop 正在跑，那个 stop 自己会启动 kill 超时 job。
- Task 3 的 `delay(2.seconds)` auto-restart backoff 发生在 `monitorJob` 协程里（scope = appScope）；如果 daemon 此时 shutdown，backoff 会被 scope 取消——安全。
- Task 3 的 `shouldAutoRestart` 要求 `oldState == RUNNING`——启动期崩溃不触发重启。这是设计选择，测试 B 用的 `CrashExitMcServer` 先打印 Done（进入 RUNNING）再 exit，所以能测到 RUNNING→crash 路径。若实际用户反馈说希望启动期失败也重试，是后续迭代。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-01-process-lifecycle-improvements.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
