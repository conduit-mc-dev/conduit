# Architecture Notes

> 记录架构决策、代码组织评估、外部项目借鉴。随项目演进更新。

## 目录

- [代码组织评估](#代码组织评估2026-04-26)
- [shared-core 测试策略](#shared-core-测试策略2026-04-26)
- [开源项目调研](#开源项目调研2026-04-26)
  - [项目概览](#项目概览)
  - [HMCL 深入借鉴](#hmcl-深入借鉴启动器架构对比)
  - [Forge/NeoForge 服务端安装](#forgeneoforge-服务端安装跨项目总结)
  - [Player 追踪](#player-追踪跨项目总结)
  - [进程生命周期改进](#进程生命周期改进跨项目总结)
  - [Mod 管理改进方向](#mod-管理改进方向跨项目总结)
  - [其他值得注意的模式](#其他值得注意的模式)
  - [明确不参考的部分](#明确不参考的部分)

---

## 代码组织评估（2026-04-26）

### 包内文件组织：扁平 vs 子包（2026-04-26）

当前所有包都是**扁平结构**（单层目录 + `{Domain}Xxx.kt` 命名约定），评估结论：**现阶段合理，暂不加子包。**

判断依据：加子包需要三个条件同时满足——文件数 >20-25、存在明确分组、分组内部有内聚性。当前无目录达标。

| 目录 | 文件数 | 状态 |
|------|--------|------|
| `shared-core/.../model/` | 17 | 全是纯数据类（12~66 行），命名约定足够 |
| `daemon/.../routes/` | 14 | 与 API 域 1:1 对应 |
| `daemon/.../service/` | 12 | 职责一致 |
| `daemon/.../store/` | 6 | 无需讨论 |
| `daemon/.../test/` | 19 | **临界点**，超过 25 个时拆为 `routes/` / `store/` / `client/` / `e2e/` 子包 |

### 当前架构优势

- **模块依赖无环**：`daemon` 和 `desktop` 仅依赖 `shared-core`，彼此零编译期耦合
- **Daemon 三层分明**：`routes/`（HTTP 映射）→ `service/`（业务编排）→ `store/`（数据持久化），无跨层调用
- **手动 DI 组合根**：`Application.module()` 显式创建所有依赖，可选参数作为测试缝合点，适合当前规模
- **数据模型单一来源**：所有 API 数据模型集中在 `shared-core/commonMain/model/`，daemon/desktop 共享
- **状态机统一**：长耗时操作走 `TaskStore → 协程 → WS 广播` 一致模式

### 已知改进点

| 问题 | 说明 | 优先级 |
|------|------|--------|
| `Json` 实例分散 | 5+ 处独立创建 `Json`，配置不完全一致。`ModRoutes.kt:55` 用裸 `Json`。应在 shared-core 定义 `ConduitJson` 统一引用（未处理） | 中 |
| Store 层紧耦合 `AppJson` | `InstanceStore` / `DaemonConfigStore` 直接 import `AppJson`，应改为构造函数注入 | 低 |
| `ProcessConfig` 位置不当 | 定义在 `InstanceStore.kt` 但只被 `ServerProcessManager` 消费，应移至 service 层 | 低 |
| `IdGenerator.generateInstanceId()` 语义溢出 | 被 `ModService` 复用来生成 mod ID，函数名与用途不符 | 低 |
| 路由层实例检查不统一 | 部分路由先 `instanceStore.get(id)` 再传给 service（双重检查），部分不检查 | 低 |

---

## shared-core 测试策略（2026-04-26）

### 背景

shared-core 当前零测试文件（无 `jvmTest` / `commonTest` 目录）。相关测试全部寄宿在 daemon 模块中：

| shared-core 被测代码 | daemon 中的测试文件 | 测试方式 |
|---|---|---|
| `ConduitApiClient` | `ConduitApiClientTest.kt` | 启动真实嵌入式 Netty |
| `MojangClient` | `MojangClientTest.kt` | MockEngine 单元测试 |
| `MojangVersionManifest` 等 | `MojangManifestParseTest.kt` | JSON 解析断言 |
| `ModrinthClient` | 无独立测试（路由测试间接覆盖） | — |
| `ConduitWsClient` | 无独立测试（E2E 间接使用） | — |

### 为什么需要改变

shared-core 未来会被 desktop 直接使用作为**本地启动器**（直接调用 `MojangClient`/`ModrinthClient` 下载游戏资源），不再仅通过 daemon API 间接使用。这意味着 shared-core 是一个被多个消费者独立依赖的库，必须有自己的测试套件。

### 实施进度

**第一步：移动可独立的测试到 `shared-core/src/jvmTest/`** ✅ 已完成（2026-04-26）

- `MojangManifestParseTest` → 已移至 `shared-core/src/jvmTest/.../download/`
- `MojangClientTest` → 已移至 `shared-core/src/jvmTest/.../download/`
- `shared-core/build.gradle.kts` 新增 `jvmTest` 依赖（kotlin-test, ktor-client-mock, coroutines-test）
- `gradle/libs.versions.toml` 新增 `kotlinx-coroutines-test`

**第二步：补充 ModrinthClient 独立测试** ✅ 已完成（2026-04-27）

`shared-core/src/jvmTest/kotlin/dev/conduit/core/download/ModrinthClientTest.kt`（7 个用例：search 字段映射/facets/limit 边界、getProjectVersions、getVersion、downloadFile、API 错误异常），commit `c53369c`。

**第三步：补充 ConduitWsClient 独立测试** — 待实施（触发条件：WsClient 加重连逻辑时）

具体用例列表由 `progress.md` Next #1 统一追踪。设计建议：先用方案 B（序列化/反序列化单测），重连逻辑加入后升级为方案 A（mock WS server）。

**保留在 daemon 中的测试**

- `ConduitApiClientTest` — 需要真实服务端
- 所有 `*RoutesTest.kt` — 测的是 daemon 路由层
- `E2ELifecycleTest` — 全链路测试

---

## 开源项目调研（2026-04-26）

> 目的：为 Forge/NeoForge 安装、Player 追踪、进程生命周期改进、Mod 管理优化提供实现参考。
> 参考仓库均已 clone 到 `~/Documents/`。UI/UX 设计参考文档由 `progress.md` 延迟项统一追踪。

### 项目概览

| 项目 | 语言/框架 | 架构模型 | 仓库路径 | 与 Conduit 最相关的能力 |
|------|-----------|----------|----------|------------------------|
| HMCL | Java / JavaFX | 单进程桌面应用 | `~/Documents/HMCL` | Forge installer profile 解析、DownloadProvider 镜像切换 |
| PrismLauncher | C++ / Qt | 单进程桌面应用 | `~/Documents/PrismLauncher` | Component 系统（loader 冲突检测）、mod 依赖解析、Modrinth 批量更新 |
| MCSManager | TypeScript / Node.js | daemon + panel 分离 | `~/Documents/MCSManager` | 进程管理、自动重启、MC 协议 Ping 追踪、mod JAR 元数据解析 |
| Pelican Wings | Go | daemon (Wings) + panel (PHP) | `~/Documents/wings` | 崩溃恢复（CrashHandler）、power lock、可配置 done 检测、console throttling |

**架构对位**：MCSManager 和 Pelican Wings 与 Conduit 同属 daemon/client 分离架构，参考价值最高。HMCL 和 PrismLauncher 是单进程桌面启动器，直接借鉴范围有限但在 Forge 安装和 mod 管理的算法层面仍有价值。

### HMCL 深入借鉴（启动器架构对比）

> HMCL (Hello Minecraft Launcher) 是成熟的开源 MC 启动器，参考仓库已 clone 到 `~/Documents/HMCL`。

**根本差异：不宜照搬**

| 维度 | HMCL | Conduit MC |
|------|------|-----------|
| 架构 | 单进程桌面应用 | daemon/client 分离 |
| UI-Core 耦合 | HMCLCore 依赖 JavaFX Property | shared-core 零 UI 依赖（KMP） |
| 异步模型 | 自定义 `Task<T>` monad 链 | Kotlin 协程 + TaskStore + WS 广播 |
| 目标 | 启动 MC 客户端 | 管理 MC 服务端 + 未来启动器 |

Conduit 在模块解耦和平台无关性上做得比 HMCL 更好，不需要向其架构靠拢。

**值得借鉴的 3 个模式**

1. **DownloadProvider URL 注入（镜像切换）** — HMCL 对 Mojang JSON 中所有嵌入 URL 统一做 `injectURL()` 重写。`MojangClient` 已借鉴前缀替换模式处理 server.jar URL（`34f766e`）。未来 desktop 增加客户端启动功能时，需处理 asset index / libraries 大量嵌入 URL，应参考 `HMCLCore/.../download/DownloadProvider.java` 做全局 URL 重写。
2. **GameBuilder 组合构建（一步到位安装）** — HMCL 用 builder 收集 `(mcVersion, forge=xxx, fabric=xxx)` 后 `buildAsync()` 输出单一复合 Task。Conduit 目前是"下载 JAR + 安装 Loader"分两步。借鉴方向：`CreateInstanceRequest` 增加可选 `loader` 字段，对外暴露单一 Task。参考 `HMCLCore/.../download/DefaultGameBuilder.java`。
3. **LibraryAnalyzer 文件推导状态** — HMCL 不存储"当前装了什么 loader"，而是分析 version.json 的 library 列表推导。`InstanceStore` 存的 `LoaderInfo` 如果磁盘 JAR 被手动替换会过时——可在 daemon 启动/loader 操作前加校验步骤。参考 `HMCLCore/.../game/LibraryAnalyzer.java`。

**HMCL 明确不参考的部分**（与总章节末尾的"明确不参考的部分"并列）

- `Task<T>` 异步框架：Kotlin 协程天然适配 C/S 架构，不需要回退到回调链
- JavaFX Observable 绑定：Compose 生态用 `StateFlow` + ViewModel
- 客户端启动逻辑：natives 解压、asset 下载等与服务端管理无关
- 六种 modpack 格式：Conduit 只需 `.mrpack`

### Forge/NeoForge 服务端安装（跨项目总结）

#### 关键发现：服务端安装 ≠ 客户端安装

HMCL 和 PrismLauncher 实现的都是**客户端**安装流程：

- **HMCL** 实现了完整的 `install_profile.json` processor pipeline（`ForgeNewInstallTask.java`），解析 processors 数组、下载 libraries、按顺序执行每个 processor JAR。但它会 `isSide("client")` 过滤，服务端 processor 有不同的参数和输出。
- **PrismLauncher** 完全绕开了 Forge 官方安装器——它使用自建的 metadata server（`meta.prismlauncher.org`）提供预处理好的版本 JSON。用户选择 Forge 版本后，Prism 下载元数据 patch 并合并到 `LaunchProfile`，没有 subprocess installer。

**服务端 Forge/NeoForge 安装的标准做法**是运行官方 installer JAR：

```
java -jar forge-{mc}-{forge}-installer.jar --installServer
```

这是一个自包含的安装流程：下载 libraries、处理 processor pipeline、生成启动脚本。输出因 Forge 版本而异：

| Forge 版本 | 安装后的启动方式 |
|-----------|----------------|
| 旧版（< 1.17） | 直接生成 patched `forge-{version}-universal.jar`，用 `java -jar forge-xxx.jar nogui` 启动 |
| 新版（≥ 1.17） | 生成 `run.sh` / `run.bat` + `libraries/` 目录，用 `bash run.sh` 或解析脚本中的 classpath 启动 |
| NeoForge | 类似新版 Forge，生成 `run.sh` + `libraries/` |

#### Conduit 实施建议

目标文件：`LoaderService.kt:108-110`（当前 throw stub）

1. 从 Forge Maven（`https://maven.minecraftforge.net/`）或 NeoForge Maven（`https://maven.neoforged.net/`）下载 installer JAR
2. 用 `ProcessBuilder` 运行 `java -jar installer.jar --installServer`，工作目录设为实例目录
3. 通过 TaskStore 广播安装进度（解析 installer stdout 的进度行）
4. 安装完成后，检测输出物（`run.sh` 或 patched JAR）来决定启动命令
5. 更新 `InstanceStore` 的 `LoaderInfo` 和 `ProcessConfig`（启动命令需要根据 Forge 版本调整）

**从 PrismLauncher 借鉴的补充点**：

- **Loader 冲突检测**：Prism 的 `Component.KNOWN_MODLOADERS`（`Component.cpp:52-58`）维护了一张互斥表——Forge 与 Fabric/Quilt/NeoForge 互斥，NeoForge 与 Forge/Fabric/Quilt 互斥。Conduit 在 `installLoader()` 入口应检查实例是否已有不兼容的 loader，避免覆盖安装。
- **参考文件**：`PrismLauncher/launcher/minecraft/Component.cpp:52-58`

### Player 追踪（跨项目总结）

#### 三种方案对比

| 方案 | 参考项目 | 工作原理 | 能获取玩家名 | 额外依赖 | 可靠性 |
|------|----------|----------|------------|----------|--------|
| stdout 日志解析 | Wings（listeners.go 的 pattern 匹配） | 匹配 `joined the game` / `left the game` 日志行 | 是 | 无 | 中——MC 版本间日志格式可能微调 |
| MC 协议 Ping | MCSManager（mc_ping.ts） | 发送 MC Server List Ping 协议包，解析响应 | 否（只有人数） | MC 协议实现库 | 高——协议稳定 |
| RCON `list` 命令 | — | 通过 RCON 发送 `list` 命令，解析返回的玩家列表 | 是 | RCON 基础设施 + server.properties 配置 | 高 |

#### 各项目的实现细节

**MCSManager**：使用 MC 协议 Ping（`mc_ping.ts`），通过 `MCServerStatus` 库连接 `localhost:port`，60 秒轮询一次。返回 `currentPlayers`（数字）、`maxPlayers`、`version`、`latency`。**不解析 stdout**，因此拿不到玩家名。Bedrock 版用 RakNet UDP 包（`mc_ping_bedrock.ts`），但未自动注册。

**Pelican Wings**：不直接做 player 追踪，但其 console 输出处理架构（`listeners.go:156-193`）提供了可配置 pattern 匹配的好模型。Done pattern 支持纯字符串匹配和 `regex:` 前缀的正则匹配，从 Panel 动态下发而非硬编码。Feature matcher（`listeners.go:196-236`）能对控制台输出做关键词匹配并发射事件（如 EULA 检测），这个模式可直接复用于 player join/leave 检测。

#### Conduit 推荐路线

**MVP：stdout 日志解析**（零额外依赖，能获取玩家名）

在 `ServerProcessManager.kt:66-87` 的输出处理循环中，在现有 `donePattern` 旁增加：

```kotlin
val joinPattern = Regex("""\[.*]: (\S+) joined the game""")
val leavePattern = Regex("""\[.*]: (\S+) left the game""")
```

匹配到时更新 `InstanceStore` 的 playerCount/players，发射 `server.players_changed` WS 事件。

**未来增强：MC 协议 Ping 补充**（解决日志格式变化风险）

参考 MCSManager 的 60 秒轮询模式，定期用 MC Ping 校准实际在线人数。两个数据源取并集：stdout 提供玩家名，Ping 提供可靠人数。

### 进程生命周期改进（跨项目总结）

#### 当前 Conduit 状态

`ServerProcessManager.kt` 的进程管理较为基础：
- 5 状态机（INITIALIZING → STOPPED ↔ STARTING → RUNNING → STOPPING → STOPPED）
- `monitorJob`（89-103 行）在进程退出后仅设状态为 STOPPED，无崩溃检测
- 无自动重启、无崩溃循环检测、无并发操作保护
- stop 有 30 秒超时后 `destroyForcibly()`

#### 1. 崩溃恢复

**Pelican Wings 模式**（最成熟）：

`CrashHandler`（`wings/server/crash.go:50-107`）的完整算法：
1. 检查进程是否从 RUNNING/STARTING → OFFLINE（排除主动 stop）
2. 查询退出码和 OOM 状态
3. 如果 exitCode=0 且非 OOM 且 `DetectCleanExitAsCrash=false`，视为正常退出
4. 检查 `lastCrash` 时间戳——如果距上次崩溃 < `timeout`（默认 60s），判定为崩溃循环，放弃重启
5. 记录崩溃活动日志（exitCode + OOM + 最后 N 行控制台输出）
6. 更新 `lastCrash` 时间戳，执行 `HandlePowerAction(Start)`

**MCSManager 模式**（更简单）：

`instance.stopped()`（`MCSManager/daemon/src/entity/instance/instance.ts:392-418`）：
- `autoRestart` + `autoRestartMaxTimes`（-1=无限）计数器
- 崩溃循环检测：如果 `now - startTimestamp < 2000ms`，打印警告（但仍然重启，只是警告）
- `ignoreEventTaskOnce()` 机制——手动 stop/restart/kill 时设置 `ignore=true`，抑制一次自动重启

**Conduit 建议实现**：

结合两者优点，在 `ServerProcessManager.monitorJob` 中：
1. 进程退出时检查：是否处于 STOPPING 状态（主动停止）→ 不触发崩溃恢复
2. 如果是 RUNNING → 退出（非主动），检查 exitCode
3. 实现 timeout 防崩溃循环（参考 Wings 默认 60 秒）
4. 加 maxRestartCount 上限（参考 MCSManager，避免无限重启）
5. 每次崩溃记录 `statusMessage`（exitCode + 最后几行日志）

#### 2. Power Lock（并发操作保护）

**Pelican Wings 模式**（`wings/system/locker.go`）：

基于 `chan bool`（容量 1）的信号量：
- `Acquire()`：非阻塞尝试，失败返回 `ErrLockerLocked`
- `TryAcquire(ctx)`：带超时阻塞等待
- `Release()`：解锁
- **Terminate（kill）绕过锁**：尝试获取但失败也继续执行——kill 信号不能被 lock 阻塞

在 `HandlePowerAction()`（`power.go:56-166`）中，lock 贯穿整个操作周期。restart = stop → wait → start 全程持锁，防止中间插入其他操作。

**Conduit 当前问题**：`ServerProcessManager.start()` 仅检查 `processes.containsKey(id)`，`stop()` 仅检查状态。没有互斥锁防止并发 start+stop 竞争。

**建议**：在 `ManagedProcess` 或实例级别加 `Mutex`/`Semaphore`，start/stop/restart 全程持锁。kill 允许绕过（参考 Wings）。用 Kotlin 的 `Mutex`（`kotlinx.coroutines.sync`）即可。

#### 3. 可配置 Done 检测

**当前状态**：已重构到 `LogPatternDetector.kt`（`ServerProcessManager.kt:39` 构造 `logDetector = LogPatternDetector()`，在输出循环中 `logDetector.detect(line)` 匹配 SERVER_DONE/OOM/端口冲突/崩溃）。pattern 本身仍硬编码在 `LogPatternDetector` 内。

**Wings 做法**（`listeners.go:156-193`）：Done pattern 从 Panel API 动态下发，支持纯字符串和 `regex:` 前缀两种模式。每种游戏服务器（"Egg"）可以有不同的 done pattern。

**建议**：MVP 阶段硬编码足够（Conduit 只管 MC 服务器）。未来如果支持模组服务器自定义启动日志，可将 pattern 移入 `InstanceConfig`，在 `LogPatternDetector` 构造时注入。

### Mod 管理改进方向（跨项目总结）

#### 1. ModStore 持久化（当前为纯内存）

**问题**：`ModStore.kt` 使用 `ConcurrentHashMap`，daemon 重启后所有 mod 元数据丢失（`.jar` 文件留在磁盘但记录消失）。

**MCSManager 做法**（`daemon/src/service/mod_service.ts:182-308`）：
- 每次请求 mod 列表时扫描 `mods/` + `plugins/` 目录
- 对每个 JAR 文件计算 `mtime + size` 指纹，命中缓存直接返回
- 缓存未命中时打开 JAR 解析元数据（fabric.mod.json / mods.toml / mcmod.info / quilt.mod.json / plugin.yml / bungee.yml）
- 缓存最大 2000 条，LRU 淘汰（利用 Map 的插入序遍历）
- 并发限制 10 个同时解析任务

**PrismLauncher 做法**（`launcher/minecraft/mod/ResourceFolderModel.cpp` + `LocalModParseTask.cpp`）：
- `QFileSystemWatcher` 监听 mods 目录变化
- 发现新/变化文件时派发 `LocalModParseTask` 到线程池
- JAR 解析支持 7 种元数据格式（含 NilLoader），找到第一个有效的即停止
- 额外维护 `.index/*.pw.toml` 元数据（packwiz 格式）作为已知 mod 的辅助信息源

**Conduit 建议**：
- 短期：daemon 启动时扫描各实例的 `mods/` 目录，从 JAR 元数据重建 `ModStore`（参考 MCSManager 的 mtime+size 缓存模式）
- 长期：考虑持久化 mod 记录到 `instance.json`（与 InstanceStore 已有的持久化模式一致），减少重启时的全量扫描

#### 2. 依赖解析

**PrismLauncher**（`GetModDependenciesTask.cpp`）是目前最完善的开源 mod 依赖解析实现：
- 递归解析，深度限制 20 层
- 跨平台：Modrinth mod 的依赖可以是 CurseForge mod，反之亦然
- 去重：按 project_id（已安装）、文件名模糊匹配（`isLocalyInstalled()`）、已在队列中三层过滤
- Quilt/Fabric 依赖 ID 互转（`getOverride()`）

**Conduit 当前状态**：完全没有依赖解析。`ModService.installFromModrinth()` 只安装用户指定的单个 mod。

**建议**：MVP 不需要依赖解析（模组服服主通常清楚自己需要什么 mod）。Desktop 端做公开启动器时再考虑，参考 Prism 的递归 + 去重算法。

#### 3. 批量更新检查

**PrismLauncher**（`ModrinthCheckUpdate.cpp`）使用 Modrinth 的批量 API：

```
POST /v2/version_files/update
Body: { "hashes": ["sha512_1", "sha512_2", ...], "algorithm": "sha512", "loaders": [...], "game_versions": [...] }
```

一次请求检查所有 mod 的更新，返回 hash→最新版本映射。

**Conduit 当前做法**（`ModService.checkUpdates()`）：逐个 mod 调用 `modrinthClient.getProjectVersions(projectId)`，N 个 mod = N 次 HTTP 请求。

**建议**：将 `checkUpdates()` 改为使用 `/v2/version_files/update` 批量端点。需要在 `ModrinthClient` 中新增 `batchCheckUpdates(hashes, algorithm, loaders, gameVersions)` 方法。

### 其他值得注意的模式

#### Console 输出缓冲

| 项目 | 机制 | 缓冲大小 | 刷新间隔 |
|------|------|----------|----------|
| MCSManager | CircularBuffer（定容环形缓冲）| 256 条 | 50ms `setInterval` 刷新，超出容量静默丢弃旧数据 |
| Pelican Wings | Rate limiter + SinkPool | 2000 行 / 100ms 窗口 | 实时推送，超过限速跳过。10ms 慢消费者超时丢弃 |
| Conduit（当前） | 无缓冲 | — | 每行立即广播 WS 消息 |

**建议**：当前逐行广播在少量客户端时没问题。如果未来出现高频输出场景（如大量 mod 加载日志），考虑参考 MCSManager 的 50ms 批量刷新模式。

#### Daemon 启动恢复

| 项目 | 机制 |
|------|------|
| Pelican Wings | `states.json` 每分钟持久化所有服务器状态；启动时读取，对之前 running 的服务器重新 start（30s 超时/服务器），对 Docker 仍在运行的重新 attach |
| MCSManager | 无自动恢复——daemon 重启后实例保持 STOP 状态，需手动或通过 `autoStart` 配置启动 |
| Conduit（当前） | 所有实例强制设为 STOPPED（`InstanceStore` 加载时），statusMessage 标记 "Recovered after daemon restart" |

Conduit 当前做法与 MCSManager 类似。Wings 的主动恢复模式更好，但需要评估安全性（daemon 崩溃重启后盲目重启所有游戏服务器是否合适）。

### 明确不参考的部分

- **PrismLauncher 的客户端 Forge processor pipeline**——服务端安装用 `--installServer`，不需要复制客户端安装逻辑
- **PrismLauncher 的 meta server 依赖**——Conduit 直接调用 Forge/NeoForge Maven，不建自己的元数据服务器
- **MCSManager 的 Docker 容器管理**——Conduit 直接管理进程，不用 Docker
- **MCSManager 的 PTY Go binary sidecar**——过度工程化，Conduit 用 `ProcessBuilder` + stdin/stdout 足够
- **Pelican Wings 的 Docker 环境抽象**——`ProcessEnvironment` 接口设计围绕 Docker，与 Conduit 的直接进程模型不匹配
- **Pelican Wings 的 Egg 脚本系统**——Conduit 只管 MC 服务器，不需要通用游戏服务器的 egg 配置
- **Pelican Wings 的 SFTP 内嵌服务器**——Conduit 用 HTTP 文件管理 API
- **MCSManager 的 `ignoreEventTaskOnce` 单次抑制机制**——Conduit 可以通过检查 STOPPING 状态来区分主动停止和崩溃，更简洁
- **PrismLauncher 的 MS OAuth 认证流程**——Conduit 有自己的 MS OAuth 实现
