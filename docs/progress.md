# Conduit MC — Progress

> 最新更新：2026-04-26（Daemon bug 修复 + 测试补全）
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目约束见根目录 `CLAUDE.md`。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [x] ~~Daemon bug 修复 + 测试补全~~ → 已完成

---

## Next（下一步）

1. [ ] Forge/NeoForge 安装支持（MVP 前）— `LoaderService.kt` 当前 Forge/NeoForge throw stub，需实现安装器
2. [ ] Player 追踪（MVP 前）— 解析 stdout join/leave 日志，更新 playerCount/players，发射 `server.players_changed` WS 事件
3. [ ] Desktop MVP 迭代 4-6（方案见 `desktop-mvp-plan.md`）

### 延迟项（MVP 后）

- [ ] Memory/TPS 监控 — 需 RCON 基础设施，实现 `memory`/`tps` 字段和 `server.stats` WS 事件
- [ ] `maxPlayers` 从 `server.properties` 实时读取（当前硬编码 20）

---

## Done

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
- [x] Desktop MVP 迭代 1-3（配对 / 创建实例 / 服务器生命周期）
  - 详细记录见 git log（`8817c9d`、`7f168e0`、`fcedad8`）
  - 自动化测试 35 个
- [x] Daemon 骨架（Ktor + 配对流程 + 实例 CRUD + 服务器生命周期）
- [x] Gradle 多模块脚手架（shared-core / daemon / desktop / web）
- [x] API 协议规范（`docs/api-protocol.md`）
- [x] 项目定位、技术栈选型、命名、Logo、README、LICENSE
- [x] 文档整合：CLAUDE.md 内联所有约束，移除 .codebuddy 多层引用
