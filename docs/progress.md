# Conduit MC — Progress

> 最新更新：2026-05-02（迭代 4 基本完成，shared-core 46 / desktop 18 / daemon 215+ tests 全绿；bug 修复：端口不生效）
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目约束见根目录 `CLAUDE.md`。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

**Desktop MVP 迭代 4（基本完成）**：server.properties 编辑器、实例删除、玩家列表已完成。剩余：UI 打磨。方案见 `desktop-mvp-plan.md`。

---

## Next（下一步）

### Desktop 依赖（当前阶段）

- [x] shared-core `ConduitWsClient` 重连逻辑 + 5 个测试用例（→ 实际 8 个）
- [x] Desktop MVP 迭代 4 剩余：实例删除 + 玩家列表 + UI 打磨
- [ ] Desktop MVP 迭代 4 收尾：UI 打磨

### 延迟项（MVP 后 / v0.2+）

- [ ] Memory/TPS 监控 — 需 RCON 基础设施，实现 `memory`/`tps` 字段和 `server.stats` WS 事件
- [ ] Mod 依赖解析 — `architecture-notes.md §2` 决定 MVP 不做；Desktop 启动器需要时参考 Prism `GetModDependenciesTask.cpp` 递归 + 去重算法
- [ ] UI/UX 设计参考文档 — 独立文件，参考 GDLauncher + MCSManager/Pelican 面板布局
- [ ] 模组下载编排逻辑从 daemon/`ModService` 抽到 shared-core — Desktop 启动器模式（不连 Daemon）直接下载模组时需要使用

### 低优先级测试（v0.2+，可选增强）

- [ ] JVM 参数构建测试（参考 HMCL `CommandBuilderTest`）
- [ ] 文件名跨平台验证 — Windows 保留名（参考 HMCL `FileUtilsTest`）
- [ ] 控制台输出限流测试（参考 Wings `rate_test.go`）
- [ ] ISO 8601 时间戳 round-trip（参考 PrismLauncher `ParseUtils_test`）
- [ ] 并发写入竞态检测（参考 Wings `utils_test.go`）

---

## Done

- [x] **Bug 修复：MC 服务器端口不生效**（2026-05-02）
  - 根因：`ServerProcessManager.startInternal()` 没有把配置的 `mcPort` 传给 Minecraft 进程
  - 修复：启动前用 `ServerPropertiesService.update()` 写入 `server-port` 到 `server.properties`
  - 测试：`ProcessLifecycleTest` 新增端口写入测试（+1）
  - 结果：daemon 215+ tests 全绿

- [x] Desktop 玩家列表显示（2026-05-02）
  - `InstanceDetailUiState` 新增 `playerNames` 字段
  - WS `PLAYERS_CHANGED` 事件处理：实时更新 `playerCount`/`maxPlayers`
  - `refreshPlayerNames()` 调用 `GET /server/status` 获取名字列表
  - RUNNING 状态下每 30s 轮询刷新名字；非 RUNNING 时取消轮询并清空
  - `PlayerBar` UI 组件：`"2/20 在线 — Steve, Alex"`，仅 RUNNING 时显示，名字超过 5 个截断
  - 测试：`InstanceDetailViewModelTest` 新增 3 个用例（WS 事件更新计数、名字加载、停止清空）
  - 结果：desktop 15 → 18 tests（+3），全绿

- [x] Desktop 实例删除（2026-05-02）
  - `ConduitApiClient` 新增 `deleteInstance(id): Unit` → `DELETE /api/v1/instances/{id}`
  - `InstanceDetailUiState` 新增 `isDeleted` 字段；`InstanceDetailViewModel` 新增 `deleteInstance()` 方法
  - `InstanceDetailScreen` HeaderBar 新增红色"删除"按钮（仅 STOPPED 状态可见）
  - `AlertDialog` 确认弹窗（显示实例名称、警告不可逆）
  - `LaunchedEffect(isDeleted)` 监听删除成功后导航回实例列表
  - 测试：`InstanceDetailViewModelTest` 新增 2 个用例（删除成功 + 409 失败）
  - 结果：desktop 13 → 15 tests（+2），全绿

- [x] `docs/superpowers/specs/` 加入 `.gitignore`（2026-05-02）
  - 与 `docs/superpowers/plans/` 一致，设计文档也作为临时文件不纳入版本控制

- [x] Desktop `ServerPropertiesScreen` + ViewModel（2026-05-02）
  - `ServerPropertiesViewModel`：分阶段编辑（`properties` vs `editedValues`），只保存变更的键值对
  - `ServerPropertiesScreen`：Compose LazyColumn 键值对编辑器，修改高亮，未保存返回确认，保存成功弹窗（含 `restartRequired` 提示）
  - `InstanceDetailScreen` HeaderBar 加 "⚙ 配置" 入口
  - 导航：`ServerPropertiesRoute` 新路由
  - 修复：`TestHelpers.mockJsonBody` 从 `Any` 改为 `inline reified`（修复序列化失败）
  - 测试：`ServerPropertiesViewModelTest` 7 个用例（load 成功/失败、updateValue 隔离、save 成功/失败、edit revert、saveSuccess 完整生命周期）
  - 结果：desktop 7 → 13 tests（+6），全绿

- [x] shared-core `ConduitWsClient` 重连逻辑（2026-05-02）
  - 指数退避重试循环（1s → 2s → 4s → 8s → 16s → 30s cap，无限重连）
  - `WsConnectionState` 枚举 + `connectionState: StateFlow` 暴露状态（DISCONNECTED/CONNECTING/CONNECTED/RECONNECTING）
  - `LinkedHashMap<String, Set<String>>` 追踪 pending subscriptions，重连后自动 `replaySubscriptions()`
  - `subscribe()` 断开时暂存、重连后重放；`unsubscribe()` 精确匹配移除
  - `connect()` 重复调用保护：先 cancel 旧 job 再启动新的
  - 测试：`ConduitWsClientTest` 8 个用例（状态、订阅追踪、覆盖、清空、部分取消、CONNECTING 转换）
  - 结果：shared-core 38 → 46 tests（+8），全绿，零 @Ignore

- [x] Daemon 功能收尾冲刺 B1-B9（2026-05-02）
  - **B1** — 修 `PUT /api/v1/java/default` silent no-op：`DaemonConfigStore` 加 `defaultJavaPath` 字段 + `updateDefaultJava()`，重启后保留。commit: `366935c`
  - **B2** — 修 `installFromModrinth` env 字段写空：从 Modrinth 响应 `client_side`/`server_side` 映射到 `ModEnvSupport`。commit: `7faaccb`
  - **B3** — 自定义 JAR 上传 ZIP 头校验：`PK\x03\x04` magic bytes 前 4 字节校验，拒绝非 ZIP 返回 400 `INVALID_FILE_FORMAT`。commit: `afd37da`
  - **B4** — `maxPlayers` 从 `server.properties` 实时读取：`InstanceStore` 恢复时读 `max-players`，不存在回退 20。commit: `9d8a9c3`
  - **B5** — 协程取消基础设施：`TaskStore.cancel()` + CANCELLED 状态、`LoaderService`/`PackService`/`ServerJarService` 的 Job 追踪和 cancel 方法、`POST /api/v1/tasks/{taskId}/cancel` 端点 + spec、`CancellationCleanupTest` 2 个活跃用例（loader install 取消 + pack build 取消）。commits: `577a33a` + `beef54b`
  - **B6** — `parseModMetadata`：解析 JAR 内 `fabric.mod.json` / `quilt.mod.json` / `META-INF/mods.toml`，提取 name、version、loaders、env。commit: `7470592`
  - **B7** — ModStore 持久化 + 启动恢复：JSON 序列化到 `<instance>/mods.json`，启动扫 mods/mods-disabled/mods-custom 目录，孤儿 mod 恢复。commit: `a5ee3a2`
  - **B8** — Modrinth 批量更新检查：`ModrinthClient.batchCheckUpdates()` 调 `POST /v2/version_files/update`，替换逐个查询。commit: `faf0472`
  - **B9** — Modrinth 搜索 client/server facets：`ModrinthClient.search` 加 `clientSide?`/`serverSide?` 参数映射到 facets。commit: `b2297c0`
  - **修复**：Windows 兼容 invalid java path 测试。commit: `0a67506`
  - **结果**：daemon 214+ tests / shared-core 38 tests 全绿，零 `@Ignore`，47/47 端点覆盖。B5 实现与原始 spec 有偏差（`POST /tasks/{id}/cancel` 替代 `DELETE /instances/{id}/tasks/{id}`），属合理设计选择——cancel 语义上比 delete 更准确，且 spec 已同步更新

- [x] Daemon 全面打磨（2026-05-01）
  - **Round 1 — Spec 补遗**：`api-protocol.md` §9.2 错误码表补 `DEVICE_NOT_FOUND` (404) + `COMMAND_FAILED` (500)；§7 `server.json.pack` 字段 Required 改为 conditional + nullable 标注
  - **Round 2 — 技术债**：`ProcessLifecycleTest` flaky test 修复（断言从 STARTING 次数改为 RUNNING 重复次数，5/5 稳定）；shared-core 加 `java-test-fixtures` 插件 → `loadFixture`/`withTempDir` 移至 `src/jvmTestFixtures/` → daemon `TestHelpers.kt` 委托调用，消除两处完全重复的 17 行实现
  - **Round 3 — 测试缺口**：
    - 协程取消清理测试 `@Ignore`（取消基础设施不存在，5 个待建能力已文档化）
    - MC 版本排序：实现 `compareMcVersions()` 比较器 + 集成到 `listVersions()` → 14 用例
    - server.properties round-trip：评论保留 / `=` 在值内 / Unicode 中文 → 3 用例
    - Mod 元数据解析测试 `@Ignore`（`parseModMetadata()` 不存在，占位文档化）
    - User-Agent 请求头断言 → 2 用例
    - 重试次数精确计数 → 1 用例
    - WebSocket 背压 smoke 测试：`RapidOutputMcServer` mock（~500 行/秒）→ WS 连接存活验证
  - **测试增长**：shared-core 21 → 38 (+17)，daemon 208 → 214 (+6，含 2 @Ignore)。全量 GREEN，零回归
  - **改动**：2 docs + 3 build + 8 test + 2 helper + 1 prod（`compareMcVersions` + `listVersions` 排序集成）。commit: `3e70c90`..`9fcce5e`（9 commits）
  - **Out of scope 确认**：ConduitWsClient 测试（缺重连逻辑）、Desktop MVP、Memory/TPS 监控、ModStore 持久化、Modrinth 批量更新 — 均保留在 Next 或延迟项

- [x] Quilt 修复 + Fabric/Quilt 测试 Windows VM 全套验证（2026-05-01）
  - **环境**：Windows 11 Pro 24H2 ARM64（VMware Fusion + Apple Silicon 宿主，Microsoft OpenJDK 21.0.10 ARM64），通过 git bundle + SSH 同步 HEAD `5cb59e0`
  - **默认套件**：`./gradlew :daemon:test` 在 Windows VM 上 **208/208 GREEN**（4 skipped = E2E 真网用例），耗时 2:32；macOS 上 1:36，ARM VM 约 1.6x overhead 属正常
  - **真网 smoke**：`CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests *ModLoaderInstallE2ETest*` **4/4 GREEN**，耗时 5:52：
    - Forge `1.20.4-49.0.14` → `win_args.txt` 生成 ✓
    - NeoForge `20.4.237` → `win_args.txt` 生成 ✓（本次未触发 2026-05-01 上午那次的 fancymodloader jar 下载 transient）
    - Fabric `0.15.11` → 覆盖 `server.jar`（179KB launcher）✓
    - Quilt `0.20.0-beta.9` → `quilt-server-launch.jar` + `libraries/` + vanilla server.jar 保留 ✓（**本次修复的主角**）
  - **跨平台覆盖确认**：LaunchTarget 三个变体（`ArgFile` / `VanillaJar` / `LoaderJar`）在 Windows 上行为与 macOS 等价；`IS_WINDOWS` 自动检测切换 `win_args.txt`；`runModLoaderInstaller` 的 `installerArgs: List<String>` 参数化在 Windows `ProcessBuilder` 下工作
  - **Flake 反观察**：`ProcessLifecycleTest.crashed process auto-restarts when enabled and within maxTimes` 在 Windows VM 上 PASS——macOS 3 跑 2 失败的 flake 在 Windows ARM 不触发，强化"事件捕获时序敏感"诊断，根因在测试设计而非生产代码
  - **同步路径**：`git bundle create main` → scp 450KB 到 VM → `git init` + `git remote add bundle` + `git fetch bundle main` + `git reset --hard FETCH_HEAD`——保留 VM 的 `build/` `.gradle/` 缓存，增量编译 < 15s

- [x] Quilt loader 安装生产 bug 修复（2026-05-01）
  - **起因**：上一条 Done（Fabric/Quilt E2E 真网 smoke）把 Quilt `@Test` 保留为 RED 信号灯，因为 `LoaderService.installQuilt()` 调用的 `meta.quiltmc.org/.../server/jar` 在 Quilt Meta OpenAPI 规范里从未定义过。用户要求"接着修"生产代码
  - **方案选择**：比较两种修复路径：(A) 拉 `/server/json` + 手动解析 14 个 libraries 逐个下载 + 手动构造 classpath 启动命令；(B) 调 Quilt Server Installer 子进程（仿 Forge/NeoForge）。选 B：代码复用率高（共享 `runModLoaderInstaller`）、installer 官方维护、产物（`quilt-server-launch.jar` + `libraries/`）即插即用
  - **Fabric vs Quilt 的 LaunchTarget 对称性修正**：本次揭示旧代码把 Fabric 和 Quilt 都设为 `LaunchTarget.VanillaJar` 是结构性错误。Fabric launcher（179KB）**覆盖** `server.jar`——vanilla MC 被替换；Quilt installer **保留** vanilla `server.jar` + 新增 `quilt-server-launch.jar`——loader 启动时 reads vanilla server.jar 作为 game jar。经本地验证：`java -jar quilt-server-launch.jar` 在无 vanilla server.jar 时抛 `Missing game jar`。修复：`LaunchTarget` 新增 `LoaderJar(fileName)` 变体，Quilt 分支切换到 `LoaderJar("quilt-server-launch.jar")`；`ServerProcessManager` 消费端新增分支
  - **`runModLoaderInstaller` 参数化**：原硬编码 `"--installServer"`（Forge/NeoForge CLI）改为接收 `installerArgs: List<String>`。Forge/NeoForge 传 `listOf("--installServer")`，Quilt 传 `listOf("install", "server", mcVersion, loaderVersion, "--install-dir=.")`
  - **installer 版本锁定**：`LoaderService.QUILT_INSTALLER_VERSION = "0.12.1"`（当前最新，Quilt Maven 2025-03-10 发布）。CLI 形态验证自 `java -jar quilt-installer-0.12.1.jar help` 实测
  - **测试改动**：
    - `LoaderInstallMockTest`：Quilt happy path 测试**删除**（installer 子进程无法 mock，与 Forge/NeoForge 无 mock 测试保持一致）；保留一个 "installer download 404" 前置用例（6 → 5 用例）
    - `LoaderVersionMappingTest`：`launch target is VanillaJar for Fabric and Quilt` 拆成两个独立测试——Fabric 继续 `VanillaJar`，Quilt 改为 `LoaderJar("quilt-server-launch.jar")`（14 → 15 用例）
    - `ModLoaderInstallE2ETest`：Quilt `@Test` 从 RED 信号灯改为正常 smoke；`ExpectedProduct` sealed class 新增 `LoaderJar(fileName, minBytes)` 变体，校验 `quilt-server-launch.jar` 存在 + vanilla server.jar 仍在；loader 版本改为 `0.20.0-beta.9`（Quilt 当前最新）
  - **验证**：
    - 默认套件 `./gradlew :daemon:test` 208 tests 全绿
    - 真网 smoke `CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests "...Quilt install produces launch jar"` **49 秒 GREEN**（从历次 RED 翻转）
    - 日志确认：Quilt installer 下载 14 libraries、生成 569 bytes `quilt-server-launch.jar`、task.completed 成功
  - **未覆盖**：未跑 Forge/NeoForge/Fabric 真网 smoke（它们行为已知）。用户如需完整 smoke 验证可跑 `CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests "*ModLoaderInstallE2ETest*"`
  - **改动文件**：`LaunchTarget.kt`（+1 变体 +注释）、`ServerProcessManager.kt`（+1 分支）、`LoaderService.kt`（installQuilt 重写 + runModLoaderInstaller 参数化 + QUILT_INSTALLER_VERSION 常量）、`LoaderVersionMappingTest.kt`（拆测试）、`LoaderInstallMockTest.kt`（删 Quilt happy path）、`TestHelpers.kt`（helper 参数 rename）、`ModLoaderInstallE2ETest.kt`（去 RED javadoc + ExpectedProduct.LoaderJar）、`progress.md`

- [x] Fabric/Quilt E2E 真网 smoke + Quilt 生产 bug 发现（2026-05-01）
  - **起因**：上一条 Done（Fabric/Quilt 双轨测试）完成后跑 `CONDUIT_RUN_SLOW_TESTS=true ./gradlew :daemon:test --tests "*ModLoaderInstallE2ETest*"`，4 个用例 1 pass + 3 fail
  - **Fabric minBytes 假设错误校正**：`0.15.11` 真实下载得到 179,054 bytes 的 ZIP launcher（不是 plan 假设的 10–50MB fat jar）。Fabric 走的是 "thin bootstrap jar" 模式——launcher 启动时再动态拉 loader。改 `ExpectedProduct.ServerJar(minBytes = 100_000)` 让断言合理
  - **Quilt E2E 保留作为 RED 信号灯**：curl + Quilt OpenAPI 规范（`meta.quiltmc.org/openapi.yaml`）证实 `/v3/versions/loader/{mc}/{loader}/server/jar` 从未定义过，唯一的服务端路由是 `/server/json`（launcher profile 而非 jar）。**换版本不能解决——生产代码 URL 形态自身错误**。经用户 review 后决定保留 `Quilt install produces server jar` @Test 作为"生产 bug 未修"的可见信号——每次跑 slow 套件都会 fail 并显示 RuntimeException，促使有人去修 `installQuilt()`。@Test 上方加长 javadoc 解释为何是预期 RED。默认 CI（不设 `CONDUIT_RUN_SLOW_TESTS`）照常 skip
  - **NeoForge 瞬时失败未修**：`20.4.237` installer 子进程报 `fancymodloader:earlydisplay/loader/spi` 和 `coremods:6.0.4` 4 个 jar 下载失败。与本次改动无关，Windows VM 同版本于 2026-05-01 早些时候通过，判定为 transient
  - **衍生发现（见技术债）**：`LoaderService.installQuilt()` URL 形态（`/server/jar`）在 Quilt meta API 上根本不存在。修复方向：(A) 改用 `/server/json` + 手动构造 classpath，或 (B) 调 Quilt Server Installer 子进程（仿 Forge/NeoForge）
  - **改动**：`ModLoaderInstallE2ETest.kt` Fabric `minBytes` 1_000_000 → 100_000 + 为 Quilt @Test 加 javadoc；`progress.md` 技术债加 Quilt 生产 bug 条目

- [x] Fabric/Quilt 安装测试（双轨：mock 必跑 + 真网可选，2026-05-01）
  - **起因**：`progress.md` Next 高优先级测试缺口之一。`LoaderService.installFabric()` / `installQuilt()`（`LoaderService.kt:182-208`）下载/写盘路径此前零自动化测试，只有 `LoaderRoutesTest` 测到 "available loaders" 和 409 前置路径
  - **策略选择**：Fabric/Quilt 与 Forge/NeoForge 关键差异 — 无 installer 子进程、无 argfile、无 libraries，纯 HTTP 下载 fat `server.jar` 一个文件。意味着 **mock HTTP 就能覆盖全部逻辑分支**，无须像 Forge/NeoForge 那样必须真网（后者的 installer 子进程用 mock 还原等于重写 installer）。采用双轨：
    - **Part A（默认跑）** `LoaderInstallMockTest.kt` 6 个用例：Fabric happy path（stable installer）、Fabric fallback（`LoaderService.kt:185-186` 全 `stable=false` 回落第一个）、Fabric installer 列表空（→ ERROR + `"No Fabric installer version found"`）、Fabric `/server/jar` 404、Quilt happy path、Quilt `/server/jar` 404（代表 "该 MC 版本无 loader" 场景）。单跑 0.47s
    - **Part C（可选 smoke）** 扩展 `ModLoaderInstallE2ETest.kt`：引入 `ExpectedProduct` sealed class（`ArgFile(path)` / `ServerJar(minBytes)`）复用 `verifyLoaderInstall`；新增 Fabric `0.15.11` + Quilt `0.26.0` 两个 `@Test`；`CONDUIT_RUN_SLOW_TESTS=true` 才跑，`assumeSlowTests()` 默认 skip
  - **Part B（mock 工厂）** `TestHelpers.kt` 追加 `createMockLoaderInstallHttpClient(installerVersionsJson, fabricServerJarResponse, quiltServerJarResponse)` + `MockLoaderResponse` sealed class（`Bytes` / `Status`）。不动既有 `createMockLoaderHttpClient`（它被 `LoaderRoutesTest` 用于 available-loaders 测试）
  - **TDD 纪律**：按 `superpowers:test-driven-development` 做 sanity demotion — 临时把用例 1 的 `assertContentEquals(bytes, ...)` 改为错误 expected、把用例 3 的 `contains("No Fabric installer version found")` 改为 `"SANITY_DEMOTION_SHOULD_FAIL"` 跑一次，确认两处确实失败（证明断言真的在比较），再恢复正确值 GREEN
  - **未改生产代码**：本次严守 "surgical changes" 原则，只加测试 + 文档；`LoaderService.kt` 零改动
  - **测试合计**：200 → 208（+6 Mock + 2 E2E skipped）；文件：2 新 + 2 编辑 + 1 docs
  - **顺带清理**：Next 测试缺口列表删除 2026-05-01 已完成但未下架的 3 条（`Content-Length` 响应头断言、启动超时检测、Fabric/Quilt 安装流程测试）；新增技术债 `ProcessLifecycleTest` 自动重启测试的偶发 flake（STARTING 瞬态在高负载下漏捕获，非本次引入）

- [x] 进程生命周期改进（MVP 前）（2026-05-01）
  - **Task 1 — STARTING 超时（60s watchdog）**：`ServerProcessManager.start()` 追加 startup timeout job，用 `transitionState(STARTING → STOPPING)` CAS 原子仲裁 SERVER_DONE 竞态。测试 +1（`ProcessLifecycleTest.startup timeout forces STOPPED`）。
  - **Task 2 — 实例级 Power Lock**：`ConcurrentHashMap<String, Mutex>` + `tryLock()` 包裹 start/stop；kill 绕过锁（Wings 模式）。失败抛 `POWER_LOCKED 409`（api-protocol.md §9.2 新增）。测试 +3（并发 HTTP、manager-级 at-most-one、确定性 POWER_LOCKED）。
  - **Task 3 — 崩溃恢复**：`DaemonConfig` 加 3 字段（`autoRestartEnabled` 默认 false、`autoRestartMaxTimes` 默认 3、`crashLoopTimeoutSeconds` 默认 60）；`ManagedProcess.intentionalExit` 区分用户 stop/kill 与崩溃；`monitorJob` 退出分支调用 `shouldAutoRestart` + `decideCrashRecovery`；循环检测基于滑动 `lastCrashAt` + `consecutiveCrashes`。测试 +3（默认关不重启 / 启用重启 / 循环放弃）。
  - **Task 4 — 合并前收尾**：`ProcessBuilder.start()` 用 try/catch 包裹——失败时将 STARTING 状态回滚为 STOPPED 并写 `statusMessage`，然后抛 `LAUNCH_FAILED 500`（原本 state 会卡死，下一次 /start 被 `INVALID_STATE` 拒绝）。api-protocol.md §11.A mermaid 补 `starting --> stopping : startup timeout` 边；§4.1 /server/start 幂等描述改为明确返回 409；§9.2 错误表新增 `LAUNCH_FAILED`。测试 +1（`start reverts to STOPPED when ProcessBuilder fails`）。
  - 测试合计：192 → 200（+8，分布：Task 1 超时 +1、Task 2 mutex +3、Task 2 fix 确定性锁 +1、Task 3 崩溃恢复 +3、Task 3 fix 手动 start 重置 +1、Task 4 收尾 launch 失败恢复 +1 — 数字有重叠，以 gradle 实测 200 为准）；文件改动：5 prod + 3 test + 2 docs。

- [x] `server.stats` 订阅一致性（2026-05-01）
  - **起因**：审计指出 spec §5.5 订阅列表把 `server.stats` 列为 "Explicit subscribe required"，但 daemon 代码从未 broadcast SERVER_STATS（`WsBroadcaster.kt:24` 仅注册频道），订阅 stats 的客户端永等不到事件
  - **发现**：审计描述部分假阳性——spec §5.3 事件表 line 1356 **早已**给 `server.stats` 标 ⚠️ 并交叉引用 "Memory/TPS 监控"（progress.md 延迟项）。真正漏洞仅是 §5.5 订阅列表 line 1390 没同步 ⚠️ 标记
  - **决策**：走 Option A（spec 标 ⚠️）而非 Option B（daemon 订阅时主动回告未实现）。理由：spec 头部 line 5 已确立 ⚠️ 为"保留但未实现"的官方 convention，Option B 会增加临时协议表面积，未来实现时需拆除（YAGNI）
  - **改动**：
    - `api-protocol.md:1390` 订阅列表补 `⚠️ _(subscribe is accepted but no events will fire until implementation; see §5.3 note and progress.md)_`
    - `WsBroadcaster.kt:23-24` 在 CHANNEL_EVENT_TYPES 的 SERVER_STATS 行上方加注释：broadcast 待 RCON 基础设施，spec §5.3/§5.5 已标 ⚠️
  - **验证**：daemon 191 测试跑一遍确认 0 回归（仅注释 + 文档改动，无代码逻辑变更）

- [x] `Content-Length` 响应头回归测试（2026-05-01）
  - **起因**：审计报告指出 spec §6.3/§6.4 明文要求 `Content-Length: {bytes}`，但 `PublicRoutes.kt` 的 `pack.mrpack` 和 `/{instanceId}/mods/{fileName}` 两个端点只调用 `call.respondFile(...)`，是否自动填 `Content-Length` 随 Ktor 引擎而异——未被任何测试断言
  - **测试结论**：对既有 `pack-mrpack serves file after build` 和 `custom mod download serves file` 两个测试加 `assertEquals(bodyBytes.size.toString(), response.headers[HttpHeaders.ContentLength])` 断言——**直接 GREEN**，证明 Ktor `respondFile`（由 `LocalFileContent` 的 `contentLength()` 驱动）本就自动填。spec 无实际违反
  - **TDD sanity demotion**：因为测试第一次就过属于 TDD 红旗（"passing immediately proves nothing"），临时把期望值改成 `"SANITY_DEMOTION_SHOULD_FAIL"` 跑一次，确认 `ComparisonFailure`——证明断言真的在比 response 里的 Content-Length 字段，不是 null==null 的自欺。还原后恢复 GREEN
  - **未改生产代码**：加显式 `call.response.header(HttpHeaders.ContentLength, ...)` 可能与 `respondFile` 内部的 Content-Length 计算冲突（`LocalFileContent` 走 HTTP streaming）。当前框架默认已合规，测试作为未来 Ktor 升级时的漂移护栏
  - daemon 191 全绿（测试数不变——仅强化断言，无新 case）

- [x] `FILE_PROTECTED` 错误码对齐（2026-05-01）
  - **起因**：2026-05-01 服务端计划审计发现 spec §9.2 已定义 `FILE_PROTECTED`（422）专用码，但 `FileService.validateNotProtected()` 一直抛 `VALIDATION_ERROR`——Desktop 无法区分"路径非法"和"受保护文件"两类语义，只能靠 HTTP status 兜底，且 status 相同（都是 422）= 无法区分
  - **改动**：`FileService.kt` 三条路径（PROTECTED_PATHS 精确匹配 / PROTECTED_PREFIXES 前缀 / `mods/*.jar` 模式）统一改抛 `FILE_PROTECTED`；`api-protocol.md:1104,1114` 两处内联文本同步从 `VALIDATION_ERROR` 修正为 `FILE_PROTECTED`（§9.2 错误码表定义不变）
  - **测试**：既有 `FileRoutesTest` 3 个代表性保护文件测试（server.jar / mods/*.jar / pack/）补加 `assertEquals("FILE_PROTECTED", error.code)` 强断言，覆盖三条代码分支；路径穿越测试保持 status-only（它们抛 `VALIDATION_ERROR`，语义不同）。FileRoutesTest 28 全绿，daemon 191 全绿
  - **TDD 纪律**：先加 RED 断言 → 运行确认 `ComparisonFailure`（实际 VALIDATION_ERROR，期望 FILE_PROTECTED）→ 生产代码 3 行改字符串 → GREEN → refactor 阶段加固另外 2 条分支的测试

- [x] Player 追踪 via Minecraft Server List Ping（2026-05-01）
  - **方案选择**：stdout 日志解析（原 Next 条目假设）/ RCON / MC Ping 三方案对比后选 Ping。理由：stdout 和 RCON 都依赖 Vanilla 英文翻译字符串（`multiplayer.player.joined` / `commands.list.players`），服务器改语言或 Mojang 改文本就炸；MC Ping 是二进制协议返回 JSON，**语言中立 + 协议稳定 + 无额外配置**。MCSManager `mc_ping.ts` 也是这个思路，行业实践验证。
  - **实现**：`shared-core/src/jvmMain/dev/conduit/core/mcping/MinecraftPingClient.kt` 手写 SLP 协议（~130 行）：TCP socket + varint + handshake + status_request + JSON 解码。零新依赖
  - **轮询**：`ServerProcessManager` 在 `SERVER_DONE` 检测到后启动 30s 间隔的 Ping job（`127.0.0.1:mcPort`），`monitorJob`（进程退出）时 cancel。首次 Ping 延迟 5s 让 MC 有时间绑端口
  - **InstanceStore**：`Instance` 加 `playerCount` / `maxPlayers` / `playerSample` 三个纯内存字段（不持久化——daemon 重启时进程也死，保留会是假数据）；`updatePlayerInfo()` 返回是否发生变化，`ServerProcessManager` 只在变化时广播 `server.players_changed`
  - **API 变更**：`server.players_changed` payload 简化为 `{ playerCount, maxPlayers }`——删掉原规范里的 `joined`/`left` 字段（Ping 只有 sample ≤12 名字，不足以可靠计算 delta）。完整名单由 `GET /server/status.players` 暴露（从 sample 读）
  - **测试 +6**：`MinecraftPingClientTest`（+4，真 TCP roundtrip：happy path、server offline → null、malformed JSON → null、无 sample 字段）；`InstanceStoreTest`（+2，update 是否报告变化 + 不持久化）。shared-core 17 → 21，daemon 190 → 192
  - **未做**：Ping 轮询的自动化测试——需要真 MC 进程，性价比低。`ModLoaderInstallE2ETest` 未来可扩展一个 "server 启动后 ping 到 0 玩家" 的 smoke 测试

- [x] Forge/NeoForge Windows VM 端到端验证（2026-05-01）
  - **环境**：Windows 11 Pro 24H2 ARM64（VMware Fusion + Apple Silicon 宿主），Microsoft OpenJDK 21.0.10 ARM64
  - **结果**：`tests="2" skipped="0" failures="0" errors="0"`，NeoForge 89.2s + Forge 67.5s
  - **确认**：`argFileName()` helper 在 Windows 下返回 `win_args.txt`，测试内 `argFile.exists()` 断言通过 = installer 实际把 `win_args.txt` 写到了 `libraries/net/{minecraftforge,neoforged}/{forge,neoforge}/{version}/` 下
  - **部署坑**：Windows 首次桥接网络默认被标成 `Public`，防火墙拦所有入站（连 ping 都不回）；`Set-NetConnectionProfile -NetworkCategory Private` + `New-NetFirewallRule -LocalPort 22 -Profile Private` 后恢复。装 sshd 还需 `Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0` + `Start-Service sshd`
- [x] Forge/NeoForge Windows 代码层支持（2026-05-01）
  - **改动**：`LaunchTarget.kt` 加 `IS_WINDOWS` val（读 `os.name`）+ `resolveLaunchTarget(loader, isWindows = IS_WINDOWS)` 可选参数。Forge/NeoForge 分支按 `isWindows` 选 `win_args.txt` 或 `unix_args.txt`。`ProcessBuilder`、`JavaDetector`、`PathValidator`、`FileService` 等已在之前迭代中 Windows-aware——审计结论见下
  - **Windows 审计结论**：除 `LaunchTarget.ArgFile` 外无其他 Windows 阻塞。潜在脆弱点（Windows 保留文件名 CON/NUL/PRN、Windows 260 字符路径限制）为极罕见边缘案例，非 MVP 阻塞
  - **测试 +2**：Forge 和 NeoForge 各加一个 `isWindows = true` 分支的 `resolveLaunchTarget` 单元测试；既有 4 个测试更新为显式传 `isWindows = false`（消除"当前 OS"漂移）。daemon 188 → 190
  - **e2e 测试兼容**：`ModLoaderInstallE2ETest` 的 `argFileName()` helper 按 OS 选文件名，同一个测试在 Linux/macOS 和 Windows 都能跑
- [x] Forge/NeoForge 安装 Phase B（Unix，2026-05-01）
  - **目标**：`LoaderService.installLoader()` 的 Forge/NeoForge stub 替换为真实安装路径；启动命令适配新 Forge/NeoForge 的 argfile 模式。MVP 仅支持 MC ≥ 1.17 的新 Forge 和 NeoForge。
  - **installer 子进程**：`runModLoaderInstaller` 下载 installer JAR → `ProcessBuilder(javaPath, -jar, installer.jar, --installServer)` → `redirectOutput(installer.log)`（避免 stdout 管道阻塞）→ `waitFor()`。成功清理 `installer.jar`、`installer.log`、`installer.jar.log`；失败保留 log 前 2000 字符作为异常 message
  - **URL 构造**：Forge `maven.minecraftforge.net/net/minecraftforge/forge/{v}/forge-{v}-installer.jar`；NeoForge `maven.neoforged.net/releases/net/neoforged/neoforge/{v}/neoforge-{v}-installer.jar`
  - **版本校验**：`validateMcVersionForForge` 在 `install()` 同步阶段调用，MC &lt; 1.17 直接 422 `UNSUPPORTED_MC_VERSION`；NeoForge 天然只发布 1.20.2+，无需额外校验
  - **启动命令适配**：新增 `LaunchTarget` 密封类（`VanillaJar` / `ArgFile`）+ `resolveLaunchTarget(LoaderInfo?)`。`ServerProcessManager.start()` 按 loader 分支：vanilla/Fabric/Quilt 走 `-jar server.jar`；Forge/NeoForge 走 `@libraries/.../unix_args.txt`（Java 9+ argfile 语法，保留用户 `jvmArgs`）
  - **错误码**：`api-protocol.md` Loader 错误表新增 `UNSUPPORTED_MC_VERSION`（422）
  - 测试 +7（196 → 203）：`validateMcVersionForForge` 边界（1.17+ 通过、1.16.5/1.12.2/1.8 拒、malformed 拒）+ `resolveLaunchTarget`（null/Fabric/Quilt → VanillaJar；Forge/NeoForge → 版本化 ArgFile 路径）
  - **未覆盖**：installer 子进程接线依赖真实 Maven 下载 + 100MB+ libraries，自动化集成测试性价比低——放 Next 列表作手工端到端验证
  - **Windows 跟进**：`LaunchTarget.ArgFile` 硬编码 `unix_args.txt`；Windows 上 Forge 生成 `win_args.txt`，需分支处理（见 Next #2）
- [x] Forge/NeoForge 版本列表（Phase A，2026-05-01）
  - **目标**：`GET /instances/{id}/loader/available` 此前仅返回 Fabric/Quilt，Forge/NeoForge 始终缺席。Phase A 补齐版本枚举路径，不触碰安装流程——让前端能看到可选版本。
  - **Forge**：`https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml`，按 `{mcVersion}-` 前缀过滤
  - **NeoForge**：`https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`；MC 版本→NeoForge 前缀通过 `neoforgeVersionPrefix`（`1.A.B` → `A.B.`，`1.A` → `A.0.`）转换
  - **XML 解析**：`parseMavenVersions` 使用 JDK 自带 `javax.xml.parsers.DocumentBuilderFactory`（JAXP）做 DOM 解析，`getElementsByTagName("version")` 取所有版本节点；启用 XXE 防护（`disallow-doctype-decl` + 禁用外部实体）。零新增依赖，正式标准库。
  - **失败容错**：每个 loader 独立 try/catch，单个源失败不影响其他 loader 列表，与现有 Fabric/Quilt 模式一致
  - **冲突检查**：经评估，`LoaderService.install():75-77` 的 `LOADER_ALREADY_INSTALLED` 已经覆盖所有 loader 互斥场景，比 Prism 的 `KNOWN_MODLOADERS` 表更严格，**无需**额外实现
  - 测试 +6（187 → 195）：
    - `LoaderRoutesTest.available loaders includes Forge filtered by MC version`
    - `LoaderRoutesTest.available loaders includes NeoForge mapped from MC version`
    - `LoaderVersionMappingTest`（4 用例）：NeoForge 前缀映射边界（带/不带 patch、非法 MC 版本）+ Maven XML 正则提取
  - **顺带修复**：`ServerRoutesTest.server status endpoint returns full status` 缺 `forceInitializing`，偶发抢到 STOPPED 状态；补齐调用
  - Fixture 文件：`daemon/src/test/resources/fixtures/loader/{forge,neoforge}-maven-metadata.xml`
- [x] WebSocket `console.input` 消息支持（2026-05-01）
  - **协议对齐**：`api-protocol.md` 5.4 节定义的 `console.input` Client→Server 消息此前未被 daemon 处理（静默丢弃）。现在 `WsRoutes.kt` 新增分支：解析 `payload.command` 后调用 `processManager.sendCommand(instanceId, command)`，与 HTTP POST `/server/command` 走同一路径。
  - **错误处理**：`sendCommand` 抛 `ApiException`（`SERVER_NOT_RUNNING` 或 `COMMAND_FAILED`）时在 WS 侧 debug 日志记录，不断开连接——与 subscribe/ping 的容错风格一致。
  - **WsMessage 扩展**：新增 `CONSOLE_INPUT` 常量和 `ConsoleInputPayload` 数据类（`command: String`），与 `ConsoleOutputPayload` 对称。
  - **测试** +2（187 → 189）：
    - `E2ELifecycleTest.console input sent via WebSocket reaches process` — mock MC server 环境下通过 WS 发 `list` 命令，断言收到 "players online" 输出
    - `ServerRoutesTest.websocket console input for unknown instance does not close connection` — 未知 instanceId 不断开连接（后续 ping 仍能 pong）
  - 文件：`shared-core/.../WsMessage.kt`、`daemon/.../WsRoutes.kt`（+ processManager 参数）、`daemon/.../Application.kt:68`

- [x] 测试系统重构 Phase 6：JSON fixture 文件化（2026-04-28）
  - **shared-core**：`ModrinthClientTest`（2 个 fixture）、`MojangClientTest`（2 个）、`MojangManifestParseTest`（3 个）共 7 个 fixture 文件迁移到 `shared-core/src/jvmTest/resources/fixtures/{modrinth,mojang}/`
  - **daemon**：`TestHelpers.kt` 的 4 个 mock JSON 块（`mockManifestJson`/`mockVersionDetailJson`/`mockVersionV1Json`/`mockVersionV2Json`）迁移到 `daemon/src/test/resources/fixtures/{modrinth,mojang}/`
  - **loadFixture 工具**：shared-core 已有；daemon 新增 `TestHelpers.kt#loadFixture(path, replacements)`，支持 `{{KEY}}` 占位符替换（用于 `$mockJarContent.size` 等动态字节长度）
  - **留在原地**：小的 request body（< 60 字符）、WebSocket 协议帧（带 `WsMessage.*` 常量引用）、Loader HTTP 响应（单行一次性使用）
  - 测试总数保持 187（纯重构），仅减少 ~100 行内联 JSON，test 数据可跨语言/编辑器语法高亮查看
- [x] 安全加固：GET 路由保护 + symlink 防护 + 路径穿越修复（2026-04-28）
  - **FileRoutes GET 保护**：GET /files 和 GET /files/content 加 `validateNotProtected`，拦截 server.jar/instance.json/pack/mods-disabled/mods-custom 读取
  - **symlink 防护**：`resolveSafePath` 改用 `toRealPath()` 做 symlink 感知的路径包含检查；处理文件/目录不存在时的 fallback
  - **PublicRoutes 修复**：custom mod 下载端点 `/{instanceId}/mods/{fileName}` 加 `sanitizeFileName` + `startsWith` 防御
  - **ModService 修复**：所有外部来源的 fileName（Modrinth API / multipart 上传 / 内部存储）统一 sanitize
  - **PathValidator 工具对象**：提取 `validateRelativePath`（原 FileService.validatePath）+ 新增 `sanitizeFileName`（提取纯文件名，拒绝 `..` 和空串）
  - 测试总数：171 → 187（+16）：PathValidatorTest 8 个、FileRoutesTest +7、PublicRoutesTest +1、ModRoutesTest +1
  - 修复 4 个安全漏洞：GET 读保护文件、symlink 逃逸、PublicRoutes 路径穿越、ModService 文件名穿越
- [x] 测试系统重构 Phase 1-5（2026-04-28）
  - **Phase 1**：提取 shared-core 共享测试工具（`TestUtils.kt`：withTempDir/mockHttpClient/jsonResponse/loadFixture），消除 5 处重复
  - **Phase 2**：统一 daemon 测试模板（`DaemonTestSetup.kt`：setupTestModule 替代 13 个 testModule 副本），forceInitializing 处理 mock 下载竞态
  - **Phase 3**：LoaderService 可 mock 化（构造函数注入 HttpClient），消除 LoaderRoutesTest/MinecraftRoutesTest 网络依赖，移除慢测试
  - **Phase 4**：路径穿越测试补全（+6 测试：PUT/DELETE/listing 的 `../`、反斜杠、绝对路径），记录 GET 路由安全缺口
  - **Phase 5**：提取 LogPatternDetector（检测 Done/OOM/端口冲突/崩溃），fixture 文件驱动的 9 个测试
  - 参考项目：HMCL（fixture 驱动）、PrismLauncher（数据驱动）、Wings（路径穿越攻击向量）、MCSManager（关键路径分析）
  - 测试总数：157 → 171（-1 慢测试 +15 新测试），净减代码 ~120 行
- [x] shared-core ModrinthClient 测试 + Windows 兼容性修复（2026-04-27）
  - **ModrinthClientTest**（7 个用例）：search 字段映射、facets 构建、limit 裁剪、getProjectVersions 列表映射、getVersion 文件哈希、downloadFile 磁盘写入、API 错误异常
  - **E2E 测试 Windows 兼容性**：用 Kotlin `MockMcServer` object 替代 bash 脚本，通过 `java -cp` 跨平台启动
  - **JavaDetector Windows 修复**：`where` 替代 `which`、`java.exe` 后缀、`Path.of()` 构建路径
  - 测试总数：140 → 147（+7 shared-core）
- [x] 开源项目调研（2026-04-26）
  - 调研 PrismLauncher、MCSManager、Pelican Wings 三个项目（加已有的 HMCL 共四个），clone 到 `~/Documents/`
  - `architecture-notes.md` 新增 "开源项目调研" 章节：项目概览、Forge 服务端安装方案、Player 追踪三方案对比、进程生命周期改进（崩溃恢复 + power lock）、Mod 管理改进方向（持久化 + 依赖解析 + 批量更新）
  - 更新 Next 任务描述：Forge/NeoForge、Player 追踪、新增进程改进任务，延迟项新增 ModStore 持久化和批量更新检查
  - UI/UX 设计参考标记为 TODO，后续独立出文档
- [x] shared-core 测试基础设施 + 架构评估（2026-04-26）
  - **架构评估文档**：新增 `docs/architecture-notes.md`，记录代码组织评估、HMCL 对比借鉴、包内文件组织决策
  - **shared-core 测试基础设施**：`build.gradle.kts` 新增 `jvmTest` source set（kotlin-test, ktor-client-mock, coroutines-test）；`libs.versions.toml` 新增 `kotlinx-coroutines-test`
  - **测试迁移**：`MojangManifestParseTest`（3 个）和 `MojangClientTest`（7 个）从 daemon 移至 `shared-core/src/jvmTest/`，改 package 为 `dev.conduit.core.download`
  - **测试策略规划**：后续两步计划（ModrinthClientTest、ConduitWsClientTest）详细记录在 architecture-notes.md
- [x] Daemon bug 修复 + Modrinth 测试补全（2026-04-26）
  - **修复 modrinthProjectId bug**：`ModrinthRawVersion` 加 `project_id`，`ModrinthVersionInfo` 加 `projectId`，`ModService.installFromModrinth()` 和 `updateMod()` 正确传递 projectId；修复前所有 Modrinth mod 更新检查静默返回空
  - **retry-download 端点归档**：`POST /instances/{id}/retry-download` 补入 `api-protocol.md` + 2 个新测试
  - **ModrinthClient 可测试化**：构造函数支持注入 `HttpClient`（与 MojangClient 模式一致），`Application.module()` 新增可选 `modrinthClient` 参数
  - **Modrinth mock + 3 个 happy path 测试**：install from Modrinth、update mod version、check updates finds newer version
  - 测试总数：135 → 140（+5）
- [x] 实例持久化 + E2E 自动化测试（2026-04-26）
  - **InstanceStore 磁盘持久化**：每个实例写 `instance.json` 到实例目录，冷启动时扫描恢复
  - 状态恢复：RUNNING/STARTING/STOPPING → STOPPED（"Recovered after daemon restart"），INITIALIZING → STOPPED（"interrupted"）
  - `PersistedInstance` 排除 taskId/statusMessage（瞬态字段），每次 mutation 自动 persist
  - `DataDirectory.instanceMetadataPath()`、`Application.module()` 参数重排（dataDirectory 在 instanceStore 之前）
  - **18 个新测试**（117 → 135）：
    - `InstanceStoreTest`（10 个）：persist/reload、状态恢复（RUNNING/INITIALIZING/STOPPED）、corrupt JSON、向后兼容、delete、update、纯内存、多实例
    - `E2ELifecycleTest`（6 个）：完整生命周期（真实 JAR @slow）、冷启动恢复、真实进程启动（mock 脚本）、WebSocket 控制台输出、命令输出、优雅停止
    - `PersistenceTest`（2 个）：DaemonConfig 跨 store 持久化、EULA 磁盘持久化
  - Mock 基础设施：MockEngine MojangClient（瞬间下载）+ bash mock MC server 脚本（模拟 "Done" 检测 + stdin 命令）
- [x] Daemon 全流程手动验证（2026-04-26）
  - 真实环境端到端验证：配对→创建实例→下载 JAR→EULA→启动 MC 服务器→控制台命令→优雅停止→删除实例
  - 全部 7 个 Phase 通过，覆盖 happy path + 错误路径（无效配对码、重名实例、未接受 EULA 启动、重复停止等）
  - WebSocket 事件验证：task.progress/task.completed（下载）、server.state_changed（生命周期）、console.output（控制台订阅）、ping/pong
  - 公共端点验证：server.json（在线/离线状态）、pack.mrpack（404 PACK_NOT_BUILT）
  - 发现：Gradle `:daemon:run` 工作目录为 `daemon/`，数据目录在 `daemon/conduit-data/`（非项目根目录），生产部署应通过 `CONDUIT_DATA_DIR` 指定绝对路径
- [x] 下载可靠性改进（`34f766e` + `28522c3`）
  - MojangClient: BMCLAPI/自定义镜像支持（前缀替换）、3 次重试（1s 间隔，4xx 不重试）、进度回调
  - ServerJarService: 接入 TaskStore，5% 粒度进度广播（task.progress/task.completed WS 事件）
  - DaemonConfig: 新增 downloadSource + customMirrorUrl 字段，热切换（无需重启）
  - HttpClient 可注入，支持 MockEngine 测试
  - User-Agent 头、Manifest 1 小时 TTL 缓存
  - 代码质量：TaskStatus/BuildState enum、task type 常量、TaskStore 去重、MAX_RETRIES 常量
  - 新增 10 个测试：3 ConfigRoutes（源配置 CRUD）+ 7 MojangClientTest（镜像/重试/进度/热切换）
  - 参考 HMCL 源码（已 clone 到 ~/Documents/HMCL）的镜像前缀替换和重试模式
  - api-protocol.md 同步更新
- [x] Daemon 全端点自动化测试覆盖（92 → 117 测试，25 个新增）
  - 提取共享 TestHelpers.kt，迁移全部 14 个旧测试文件
  - ServerRoutesTest: +7（stop/kill/restart 错误路径 + restart with eula，WS ping/pong/subscribe/event）
  - InstanceRoutesTest: +4（explicit mcPort, PORT_CONFLICT, update nonexistent, name conflict on update）
  - PairRoutesTest: +2（revoke all devices, rate limiting 429）
  - LoaderRoutesTest: +2（available loaders, install when initializing）
  - ModRoutesTest: +1（toggle nonexistent mod 404）
  - FileRoutesTest: +2（delete nonexistent 404, 10MB size limit 413）
  - PublicRoutesTest: +3（Cache-Control header, pack.mrpack 304, custom mod 304）
  - ModrinthRoutesTest: +1（project versions auth）
  - WebSocket: ping/pong, instance.created 事件接收, unsubscribe 连接保持
  - 显式延后：Modrinth 快乐路径（需 mock）、server start 快乐路径（需真实 jar）
- [x] Phase 6: Public 端点（3 端点）— server.json 动态聚合, pack.mrpack 下载, custom mod 下载
  - 新增 `PublicModels.kt` (shared-core), `PublicRoutesTest.kt` (8 tests)
  - 扩展 `PublicRoutes.kt`：server.json 聚合实例/mod/pack/进程状态，ETag 条件请求支持
  - publicEndpointEnabled=false 时 server.json 返回 404
  - **全部 54 个 API 端点实现完成（21 原有 + 33 新增）**
- [x] Phase 5: 任务系统 + Loader + Pack（7 端点）— Loader get/available/install/uninstall, Pack info/build/status
  - 新增 `TaskModels.kt`, `LoaderModels.kt`, `PackModels.kt` (shared-core)
  - 新增 `TaskStore.kt`, `PackStore.kt`, `LoaderService.kt`, `PackService.kt`, `LoaderRoutes.kt`, `PackRoutes.kt`
  - 后台任务基础设施（TaskStore + task.progress/task.completed WS 事件）
  - Fabric/Quilt 安装器支持（下载 server jar），Forge/NeoForge 预留
  - mrpack ZIP 生成（modrinth.index.json），自动版本递增
  - daemon/build.gradle.kts 加 Ktor client 依赖
- [x] Phase 4: Mod 管理（7 端点）— list/install/upload/remove/update/toggle/check-updates
  - 新增 `ModModels.kt` (shared-core), `ModStore.kt`, `ModService.kt`, `ModRoutes.kt`, `ModRoutesTest.kt` (9 tests)
  - 3 目录管理（mods/, mods-disabled/, mods-custom/）
  - Multipart 上传、SHA-1/SHA-512 哈希计算、pack.dirty WS 事件广播
  - DataDirectory 加 modsDir(), modsDisabledDir(), modsCustomDir()
- [x] Phase 3: Modrinth 代理 + Java 管理（4 端点）— Modrinth search/versions proxy, Java installations/default
  - 新增 `ModrinthModels.kt`, `JavaModels.kt` (shared-core), `ModrinthClient.kt` (shared-core jvmMain)
  - 新增 `JavaDetector.kt`, `ModrinthRoutes.kt`, `JavaRoutes.kt`, 测试文件
  - ModrinthClient: snake_case → camelCase 映射，facets 构建，User-Agent 头
  - JavaDetector: 跨平台 Java 安装扫描（macOS/Linux/Windows），`java -version` 探测
- [x] Phase 2: Server Properties + 文件管理（6 端点）— server.properties GET/PUT, files GET/GET content/PUT content/DELETE content
  - 新增 `FileModels.kt` (shared-core), `FileService.kt`, `ServerPropertiesService.kt`, `FileRoutes.kt`, `FileRoutesTest.kt` (13 tests)
  - 路径穿越保护（拒绝 `..`）、保护文件黑名单（server.jar, mods/*.jar, pack/, instance.json）
  - MIME 类型推断、10MB 写入限制、隐式目录创建
- [x] Phase 1: 配置基础（6 端点）— Daemon Config GET/PUT, JVM Config GET/PUT, Invite GET/PUT
  - 新增 `ConfigModels.kt` (shared-core), `DaemonConfigStore.kt`, `ConfigRoutes.kt`, `ConfigRoutesTest.kt` (14 tests)
  - InstanceStore 增加 `publicEndpointEnabled` 字段、JVM 配置方法
  - DaemonConfigStore 支持 `config.json` 磁盘持久化
  - JVM Config PUT 支持 partial update（区分字段缺失 vs 显式 null）
  - ConduitApiClient 增加 6 个新方法
- [x] Daemon 已实现端点与 `api-protocol.md` 规范对齐（~30 处偏差修正）
  - ServerModels: `ServerStatusResponse` 9 字段对齐规范, `EulaResponse` 加 `eulaUrl`, 新增 `CommandAcceptedResponse`/`MemoryInfo`
  - WsMessage: `STATE_CHANGED` → `server.state_changed`, `StateChangedPayload` 改 `oldState/newState`, `ConsoleOutputPayload` 加 `level`
  - WsBroadcaster: 频道订阅模型（`console`/`stats` 需显式订阅，其余默认广播）+ `broadcastGlobal()` 全局事件
  - ServerRoutes: 全部 7 端点返回正确类型/状态码; `start` 幂等+INSTANCE_INITIALIZING; 新增 `restart`; `command` 返回 200+body
  - ServerProcessManager: uptime 追踪, `stop`/`kill` 正确错误码, `broadcastStateChanged(oldState, newState)`
  - MinecraftRoutes: 包装响应 `{versions, cachedAt}`, `?type=` 过滤, `502 MOJANG_API_ERROR`
  - InstanceStore: delete 区分 `INSTANCE_INITIALIZING`/`INSTANCE_RUNNING`, resetToInitializing 用 `INSTANCE_RUNNING`
  - InstanceRoutes: mcVersion 非空校验, 广播 `instance.created`/`instance.deleted`
  - PairRoutes: 限速器（5 次/分钟/IP）, `429 RATE_LIMITED`
  - WsRoutes: 解析 `channels` 字段, 应用级 `ping`→`pong`
  - MojangClient: `listVersions(type)` 支持 release/snapshot/all, 暴露 `cachedAt`
  - LoaderInfo 加 `mcVersion?`, SubscribeRequest/UnsubscribeRequest 加 `channels`
  - ConduitApiClient: start/stop/kill 返回 `ServerStatusResponse`, sendCommand 返回 `CommandAcceptedResponse`, listMinecraftVersions 返回包装类型
  - Desktop ViewModel: 适配新 API 返回类型
  - 延迟项（需后续基础设施）: playerCount/maxPlayers 实时追踪, memory/tps, server.stats/players_changed 事件
> 更早的条目（v0.0 初始化：项目脚手架、API 协议制定、Desktop MVP 迭代 1-3、Daemon 骨架）已归档至 [`progress-archive.md`](progress-archive.md)。
