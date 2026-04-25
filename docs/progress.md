# Conduit MC — Progress

> 最新更新：2026-04-25
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目决策背景见 [`project-context.md`](./project-context.md)。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [ ] Desktop MVP 迭代 4-6（配置管理 / 实时更新 / JVM 配置 + 打磨）
  - 详细方案见 [`desktop-mvp-plan.md`](./desktop-mvp-plan.md)

---

## Next（下一步）
- [ ] Loader / Mod 管理里程碑（shared-core 复用层：Daemon + Desktop 启动器共用）
- [ ] Web 管理面板（Compose WasmJS，Daemon 内置 serve）

---

## Done

- [x] Desktop MVP 迭代 3：服务器生命周期（启动 / 停止 / 控制台）
  - Daemon：`ServerProcessManager`（子进程管理 + 状态检测 + stdin/stdout）
  - Daemon：`EulaService`（eula.txt 读写 + 目录自动创建）
  - Daemon：`ServerRoutes`（status/eula/start/stop/kill/command 六个端点）
  - Daemon：`WsBroadcaster` + `WsRoutes`（WebSocket 广播 + subscribe/unsubscribe + token 认证）
  - Daemon：`InstanceStore` 线程安全重构（`var` → `val` + `compute()` + `copy()` 原子替换）
  - shared-core：`ServerModels`（ServerStatusResponse / EulaResponse / AcceptEulaRequest / SendCommandRequest）
  - shared-core：`WsMessage` 扩展（常量 + payload 类型：ConsoleOutputPayload / StateChangedPayload）
  - shared-core：`ConduitApiClient` 新增 8 个生命周期方法 + 204 NoContent 支持
  - shared-core：`ConduitWsClient`（Ktor WebSocket 客户端 + SharedFlow 消息流）
  - Desktop：`InstanceDetailScreen`（控制台 LazyColumn + 命令输入 + EULA 对话框 + 状态操作按钮）
  - Desktop：`InstanceDetailViewModel`（REST + WebSocket 编排，1000 行控制台缓冲）
  - Desktop：实例列表 Card 点击导航 → 详情页
  - 自动化测试：9 个新测试（ServerRoutesTest），总计 35 个
  - 手动验证：Daemon 端到端 配对 → 创建 → EULA 检查(false) → 启动被拒(409) → 接受 EULA → 启动(starting→running) → /list 命令(204) → /stop(stopping→stopped) ✓
- [x] Desktop MVP 迭代 2：创建实例 + server.jar 下载
  - shared-core：`MojangClient`（Ktor Client + CIO）获取 Mojang 版本清单 + 下载 server.jar（SHA-1 校验）
  - shared-core：`MojangVersionManifest` / `MojangVersionDetail` / `MojangDownloadEntry` 等数据模型
  - Daemon：`ServerJarService` 后台下载 + 二阶段实例创建（`INITIALIZING` → `STOPPED`）
  - Daemon：`DataDirectory` 可配置数据目录（`CONDUIT_DATA_DIR` 环境变量）
  - Daemon：`MinecraftRoutes` 改为真实 Mojang manifest 获取（101 个 release 版本）
  - Daemon：`Application.module()` 参数化支持测试注入
  - shared-core：`ConduitApiClient.createInstance()` 新增
  - shared-core：`InstanceSummary` 新增 `taskId` + `statusMessage` 字段
  - Desktop：创建实例屏幕（MC 版本下拉框 + 名称 + 描述 + 端口）
  - Desktop：实例列表添加"创建实例"按钮 + 空状态引导
  - Desktop：导航新增 `CreateInstanceRoute`
  - 自动化测试：4 个新测试（MojangManifestParseTest 3 + ConduitApiClientTest 1），总计 26 个
  - 手动验证：Daemon + Desktop 端到端创建实例 → 下载 server.jar（55MB）→ 状态转 stopped ✓
- [x] Desktop MVP 迭代 1：端到端联通（配对 + 实例列表）
  - 架构：Koin 4.2（DI）+ JetBrains navigation-compose 2.9（导航）+ shared-core API Client 分层
  - shared-core：`ConduitApiClient`（Ktor Client + CIO）、`ConduitApiException`、`MinecraftVersion` 模型
  - Daemon：`GET /api/v1/minecraft/versions` 桩端点（硬编码 10 个版本）
  - Desktop：配对屏幕（连接 → 输入配对码 → 配对）→ 实例列表屏幕
  - 依赖升级：kotlinx-datetime 0.6.2 → 0.7.1（`Instant` 迁移到 `kotlin.time`）
  - 新增依赖：Koin BOM 4.2.1、navigation-compose 2.9.2、lifecycle-viewmodel-compose 2.9.6、Ktor Client CIO、kotlinx-coroutines-swing
  - 自动化测试：7 个新测试（MinecraftRoutesTest 2 + ConduitApiClientTest 5），总计 22 个
  - 手动验证：Daemon + Desktop 端到端配对 → 实例列表显示"暂无服务器实例" ✓
- [x] Daemon 骨架（Ktor + 配对流程 + 实例 CRUD）
  - 基础设施：ContentNegotiation、StatusPages 错误信封、Bearer 认证、WebSocket、CORS
  - 配对流程：6 位配对码 → token 生成 → SHA-256 哈希存储 → 设备列表 / 撤销
  - 实例 CRUD：创建 / 列出 / 获取 / 更新 / 删除，端口自动分配，名称唯一校验
  - shared-core 数据模型：错误信封、配对 DTO、实例 DTO、WS 消息信封
  - 自动化测试：14 个测试用例（ktor-server-test-host），覆盖认证 / 配对 / 实例全流程
  - 数据存储为内存态，持久化留给下个里程碑
- [x] Gradle 多模块脚手架（`shared-core` / `daemon` / `desktop` / `web`）
  - Gradle 9.3 wrapper + Kotlin 2.3.21 + Compose 1.10.3 + Ktor 3.4.3
  - `shared-core`：KMP 库（JVM + WasmJS），kotlinx.serialization + coroutines
  - `daemon`：Ktor Netty 服务器，端口 9147，`/public/health` 端点可用
  - `desktop`：Compose Desktop 窗口，含原生打包配置（Dmg/Msi/Deb）
  - `web`：Compose WasmJS 桩模块，可编译但无业务逻辑
  - 版本目录（`gradle/libs.versions.toml`）集中管理所有依赖版本
  - `./gradlew build` 全模块编译通过
- [x] API 协议规范（`docs/api-protocol.md`）
  - Host ↔ Daemon REST + WebSocket 设计（多实例、多设备）
  - Client ↔ Daemon 公开端点设计（按实例隔离）
  - `server.json` schema
  - 邀请链接格式（`conduit://host:port/instanceId`）
  - pair token 流程（多设备配对）
- [x] 项目定位拍板
- [x] 技术栈选型（Kotlin + Compose Multiplatform + Ktor + GPLv3）
- [x] 名字敲定（Conduit MC）
- [x] GitHub 组织 + 仓库创建、topics、description 配置
- [x] README v0.1（双语）
- [x] LICENSE（GPLv3 全文）
- [x] `.gitignore`
- [x] Logo 设计（9×9 像素潮涌核心 + Inter 字标 + slogan）
  - `docs/logo-icon.svg` / `docs/logo-full.svg`
  - PNG 光栅版（256 / 512 / 1600）
- [x] `docs/branding.md`（设计资产说明、调色板、商标免责）
