# Conduit MC — Progress

> 最新更新：2026-05-01（Forge/NeoForge 版本列表支持完成，Phase A）
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目约束见根目录 `CLAUDE.md`。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

（暂无，等待下一任务）

---

## Next（下一步）

1. [ ] shared-core ConduitWsClient 测试 — 等加重连逻辑时一起实施（5 个用例）
2. [ ] Forge/NeoForge 安装 Phase B（MVP 前）— Phase A（版本列表）已完成；剩余：`LoaderService.kt:109-110` 仍 throw stub，需(1) 下载 installer JAR、(2) `--installServer` subprocess 执行 + 进度解析、(3) 产物检测（`run.sh` vs patched JAR）、(4) `ServerProcessManager.kt:51-57` 启动命令从硬编码 `java -jar server.jar` 改为按 loader 分支（新 Forge/NeoForge 用 `bash run.sh` 或从脚本解析 classpath）。仅支持 MC ≥ 1.17 的新 Forge 和 NeoForge（旧 Forge 返回 `UNSUPPORTED_MC_VERSION`）。详见 `architecture-notes.md` "Forge/NeoForge 服务端安装" 章节
3. [ ] Player 追踪（MVP 前）— MVP 用 stdout 日志解析（`joinPattern`/`leavePattern`），未来用 MC Ping 补充。详见 `architecture-notes.md` "Player 追踪" 章节
4. [ ] 进程生命周期改进（MVP 前）— 崩溃恢复（Wings CrashHandler 模式 + MCSManager maxTimes）、power lock（并发 start/stop 保护）。详见 `architecture-notes.md` "进程生命周期改进" 章节
5. [ ] Desktop MVP 迭代 4-6（方案见 `desktop-mvp-plan.md`）

### 技术债（非阻塞）

- [ ] shared-core/daemon 测试工具跨模块共享 — 目前 `loadFixture` 和 `withTempDir` 在 shared-core `TestUtils.kt` 和 daemon `TestHelpers.kt` 各有一份完全相同的实现。根因是无 `java-test-fixtures` 插件或独立 `test-utils` 模块。方案：给 shared-core 加 `java-test-fixtures` → daemon `testImplementation(testFixtures(project(":shared-core")))`

### 延迟项（MVP 后）

- [ ] Memory/TPS 监控 — 需 RCON 基础设施，实现 `memory`/`tps` 字段和 `server.stats` WS 事件
- [ ] `maxPlayers` 从 `server.properties` 实时读取（当前硬编码 20）
- [ ] ModStore 持久化 — 当前纯内存，daemon 重启丢失。参考 MCSManager 的 JAR 元数据扫描 + mtime/size 缓存
- [ ] Modrinth 批量更新检查 — 改用 `POST /v2/version_files/update` 替代逐个查询
- [ ] UI/UX 设计参考文档 — 独立文件，参考 GDLauncher + MCSManager/Pelican 面板布局

### 测试缺口清单（按优先级，参考 HMCL/PrismLauncher/Wings/MCSManager 调研）

**高优先级（MVP 前应补）**
- [ ] 协程取消清理 — 取消安装/下载后无残留文件、状态回 STOPPED（参考 HMCL `TaskTest`）
- [ ] `pack.mrpack` 响应头断言 — `api-protocol.md` 第 1448 行要求 `Content-Length: {bytes}`，`PublicRoutes.kt:95-98` 未显式设置（Ktor `respondFile` 可能自动填）。需测试断言验证；缺失则手工添加
- [ ] Fabric/Quilt 安装流程测试 — `installFabric()`/`installQuilt()` 执行路径无测试（参考 MCSManager `quick_install.ts`）
- [ ] 启动超时检测 — `Done` 永不出现时实例停留在 STARTING 无限期

**中优先级（v0.2 前应补）**
- [ ] MC 版本排序正确性 — shuffle + sort = 已知顺序（参考 HMCL `GameVersionNumberTest`）
- [ ] server.properties round-trip 特殊字符 — `#` 注释、`=` 在值内、Unicode（参考 PrismLauncher `INIFile_test`）
- [ ] WebSocket 背压/满 channel 行为（参考 Wings `sink_pool_test.go`）
- [ ] 请求头断言 — 验证 User-Agent/Authorization（参考 Wings `http_test.go`）
- [ ] 重试次数精确计数验证（参考 Wings `http_test.go`）
- [ ] Mod 元数据解析 — fixture JAR 中的 fabric.mod.json/mods.toml/plugin.yml（参考 MCSManager/PrismLauncher）

**低优先级（可选增强）**
- [ ] JVM 参数构建测试（参考 HMCL `CommandBuilderTest`）
- [ ] 文件名跨平台验证 — Windows 保留名（参考 HMCL `FileUtilsTest`）
- [ ] 控制台输出限流测试（参考 Wings `rate_test.go`）
- [ ] ISO 8601 时间戳 round-trip（参考 PrismLauncher `ParseUtils_test`）
- [ ] 并发写入竞态检测（参考 Wings `utils_test.go`）

---

## Done

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
