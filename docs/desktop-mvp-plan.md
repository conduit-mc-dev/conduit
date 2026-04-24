# Desktop MVP — 交替推进方案

> 创建于 2026-04-25
> 状态：迭代 1 待实施

## Context

Daemon 骨架已完成（配对 + 实例 CRUD + 14 个测试），Desktop 仅有一个 stub 窗口。目标：交替推进 Daemon 后端能力和 Desktop 前端 UI，每个迭代交付一个垂直完整的功能切片。

数据仍为内存态，持久化留给下个里程碑。

## 架构决策

### 1. shared-core 分层（学习 HMCL 的 Core 模式）

HMCL 将所有核心逻辑（mod 下载、loader 安装、版本解析、启动器逻辑）放在 `HMCLCore` 纯后端库中，UI 只是薄壳。我们沿用同样的思路：

**`shared-core` 承载所有可复用的业务逻辑**（不仅是数据模型）：
- `core/api/` — Daemon HTTP API 客户端（Desktop 管理模式用）+ WebSocket 客户端
- `core/download/` — Mojang 版本清单解析、server.jar/client.jar 下载（Daemon 和 Desktop 启动器都用）
- `core/loader/` — Fabric/Forge/NeoForge 安装逻辑（Daemon 服务端 + Desktop 客户端共用）
- `core/mod/` — Modrinth API 客户端、mod 文件管理（Daemon + Desktop 共用）
- `core/pack/` — mrpack 打包/解析（Daemon 构建、Desktop 消费）

**Daemon 是 shared-core 逻辑的 HTTP 包装层**：路由 → 调 shared-core → 返回结果
**Desktop 管理模式通过 API Client 远程调用 Daemon**
**Desktop 启动器模式直接调用 shared-core 本地执行**（下载 mod、安装 loader、启动 MC）

> 注意：初期只建 `core/api/` 和数据模型。`core/download/`、`core/loader/`、`core/mod/` 在后续迭代按需添加。

### 2. DI → Koin 4.2

KMP 生态事实标准（~10k stars），支持 Desktop + WasmJS，纯 Kotlin DSL 无代码生成，Compose 集成（`koinViewModel()`），可选 KSP 编译期验证。

HMCL 没用 DI 框架（Java 静态单例模式），但这不适用于 KMP 跨平台场景。

### 3. 导航 → JetBrains 官方 navigation-compose 2.9

JetBrains 维护，支持 Desktop + WasmJS（含浏览器历史），类型安全路由（`@Serializable`），嵌套导航 + 深度链接 + 回退栈。

竞品对比：Voyager 半停更（最后发布 2024.10），Decompose 过重（需买入整套 Component 模型）。

### 4. shared-core API Client

`shared-core/src/jvmMain/` 的 `ConduitApiClient`（Ktor Client + CIO 引擎）。初始只放 `jvmMain`，Web 面板时提升到 `commonMain` + expect/actual 引擎。

### 5. 新增依赖

| 依赖 | 模块 | 用途 |
|---|---|---|
| ktor-client-core | shared-core (jvmMain) | HTTP 客户端核心 |
| ktor-client-cio | shared-core (jvmMain) | CIO 引擎 |
| ktor-client-content-negotiation | shared-core (jvmMain) | JSON 序列化（注意：已在版本目录中作为测试依赖存在，需复用别名） |
| ktor-serialization-kotlinx-json | shared-core (jvmMain) | JSON 序列化实现 |
| ktor-client-websockets | shared-core (jvmMain) | WebSocket 客户端（迭代 3） |
| koin-bom 4.2.1 | 全局 | DI BOM 统一版本 |
| koin-core | shared-core | DI 核心 |
| koin-compose-viewmodel | desktop | Compose ViewModel 集成 |
| navigation-compose | desktop | 官方导航 |
| kotlinx-coroutines-swing | desktop | Compose 协程调度器 |

---

## 迭代 1：端到端联通（配对 + 实例列表）

**目标**：最小垂直切片 — Desktop 能连上 Daemon，完成配对，看到实例列表。

### Daemon 工作

1. **`GET /api/v1/minecraft/versions` 桩端点** — 返回硬编码的近期 MC 版本列表（**迭代 2 替换为真实 Mojang manifest 获取**）
   - 新建 `shared-core/.../model/MinecraftModels.kt`
   - 新建 `daemon/.../routes/MinecraftRoutes.kt`
   - 修改 `Application.kt` 挂载路由
   - 注意：此阶段创建实例仍返回 `state=STOPPED`（无 `taskId`），二阶段创建（`INITIALIZING` → `STOPPED` + `taskId`）在迭代 2 实现

### shared-core 工作

2. **添加 Ktor Client + Koin 依赖** — 修改 `libs.versions.toml` + `shared-core/build.gradle.kts`
3. **创建 `ConduitApiClient`** — `shared-core/src/jvmMain/.../api/ConduitApiClient.kt`
   - `health()`, `initiatePairing()`, `confirmPairing()`, `listInstances()`, `listMinecraftVersions()`
4. **创建 `ConduitApiException`** — `shared-core/src/commonMain/.../api/ConduitApiException.kt`

### Desktop 工作

5. **配置 Koin + navigation-compose 依赖** — 修改 `desktop/build.gradle.kts`
6. **Koin 模块声明 + Application 初始化** — 修改 `Main.kt`
7. **配对屏幕** — `PairScreen.kt` + `PairViewModel.kt`
8. **实例列表屏幕** — `InstanceListScreen.kt` + `InstanceListViewModel.kt`
9. **导航图** — `NavHost` + `composable<Route>` 路由

### 验证清单

**自动化测试**：
- Daemon：`MinecraftRoutesTest.kt`（版本列表返回 + 认证检查）
- shared-core：`ConduitApiClientTest.kt`（health / pairing / listInstances 序列化往返）

**Happy path（手动）**：
1. `./gradlew :daemon:run` 启动 Daemon
2. `./gradlew :desktop:run` 启动 Desktop
3. Desktop 输入 `http://localhost:9147`，点击连接，显示健康状态
4. Daemon 控制台获取配对码（或通过测试 API），Desktop 输入配对码，配对成功
5. 自动跳转到实例列表页，显示空列表

**关键异常**：
- 输入错误的 Daemon URL → 显示连接失败提示（不崩溃）
- 输入错误的配对码 → 显示"配对码无效"（不崩溃）
- Daemon 未启动时 Desktop 尝试连接 → 显示网络错误提示

---

## 迭代 2：创建实例 + server.jar 下载

**目标**：从 Desktop 创建实例，Daemon 从 Mojang 下载 server.jar。

### Daemon 工作

1. **Mojang 版本清单获取** — `shared-core/.../download/MojangManifest.kt`（shared-core，Daemon 和 Desktop 启动器共用）
2. **server.jar 下载服务** — `daemon/.../service/ServerJarService.kt`
3. **二阶段实例创建** — 初始 `INITIALIZING`，下载完成转 `STOPPED`
4. **可配置数据目录** — 环境变量 `CONDUIT_DATA_DIR`

### shared-core + Desktop 工作

5. **`createInstance()`** 加入 API Client
6. **创建实例表单** — MC 版本下拉框 + 名称 + 描述 + 端口
7. **实例列表增加手动刷新**

### 验证清单

**自动化测试**：
- Mojang manifest JSON 解析（用 fixture 数据，不依赖网络）
- 实例创建返回 `state=INITIALIZING` + `taskId` 非空
- API Client `createInstance()` 序列化往返

**Happy path（手动）**：
1. Desktop 实例列表点击"创建实例"
2. MC 版本下拉框有数据（从 Daemon API 拉取）
3. 填写名称 + 选版本 → 点击创建 → 回到列表，新实例状态 `initializing`
4. 几秒后点击刷新，状态变为 `stopped`
5. 确认磁盘 `conduit-data/instances/{id}/server.jar` 文件存在

**关键异常**：
- 创建同名实例 → 显示 409 冲突错误
- Mojang CDN 下载失败（断网）→ 实例不卡在 `initializing`，有错误反馈
- MC 版本列表加载失败 → 表单显示错误提示，不阻塞其他功能

---

## 迭代 3：服务器生命周期（启动 / 停止 / 控制台）

**目标**：核心闭环 — 启动 MC 服务器，实时看控制台输出，发送命令，停止。

### Daemon 工作

1. **MC 进程管理器** — `ServerProcessManager.kt`（子进程管理 + 状态检测 + stdin/stdout）
2. **EULA 管理** — 读写 `eula.txt`
3. **服务器生命周期路由** — `ServerRoutes.kt`（status/eula/start/stop/kill/command）
4. **WebSocket 路由** — `WsRoutes.kt` + `WsBroadcaster.kt`（console.output + state_changed）

### shared-core + Desktop 工作

5. **服务器状态模型** — `ServerModels.kt`
6. **API Client 增加生命周期方法**
7. **WebSocket 客户端** — `ConduitWsClient.kt`
8. **实例详情屏幕** — 状态徽章 + 控制台 + 命令输入 + EULA 对话框

### 验证清单

**自动化测试**：
- EULA 状态检查 / 接受端点
- 服务器启动返回 `state=starting`，未接受 EULA 时返回 409
- WebSocket 连接 + 订阅 + 收到 console.output 事件（用 Ktor test WS client）
- 进程管理器日志解析逻辑（用 fixture 日志行）

**Happy path（手动）**：
1. Desktop 点击一个 `stopped` 状态的实例 → 进入详情页
2. 点击"启动" → 弹出 EULA 接受对话框 → 点击"接受"
3. 服务器开始启动，控制台实时显示 MC 启动日志
4. 状态徽章从 `starting` 变为 `running`
5. 在命令输入框输入 `/list` → 控制台显示在线玩家
6. 输入 `/stop` → 服务器优雅停止，状态变为 `stopped`

**关键异常**：
- 未接受 EULA 就点启动 → 弹出 EULA 对话框（不是 500 错误）
- 服务器崩溃（进程异常退出）→ 状态自动回到 `stopped`，控制台显示崩溃日志
- WebSocket 断开 → 自动重连，重连后恢复控制台流
- 对 `stopped` 状态的实例点击"停止" → 按钮禁用或提示已停止

---

## 迭代 4-6（概要，开始时再细化验证清单）

| 迭代 | 内容 | 关键验证点 |
|---|---|---|
| 4 | server.properties 读写 + 实例删除（清理磁盘）+ 玩家列表 + UI 打磨 | 删除实例后磁盘目录清除；修改 motd 后重启生效 |
| 5 | WebSocket 实时更新实例列表 + 下载进度条 + TPS/内存统计 | 实例列表状态实时变化（不需手动刷新）；进度条准确反映下载进度 |
| 6 | JVM 配置编辑 + 重启 + 实例编辑 + 整体打磨 | 修改 JVM 参数后重启生效；实例重命名后列表同步更新 |

> 迭代 4-6 完成后进入 **Loader / Mod 管理里程碑**，`shared-core/core/loader/` 和 `core/mod/` 同时被 Daemon（服务端 mod 管理）和 Desktop 启动器（客户端 mod 同步）使用。

---

## 参考仓库

| 仓库 | 参考重点 |
|---|---|
| [HMCL](https://github.com/HMCL-dev/HMCL) | `HMCLCore/src/.../download/`（Mojang API）、`.../mod/modrinth/`（Modrinth 客户端）、`.../download/forge/`（loader 安装） |
| [OctoMeter](https://github.com/ryanw-mobile/OctoMeter) | `AppNavigationHost.kt`（导航模式）、`di/`（Koin 配置）、`destinations/`（屏幕结构） |
| [PeopleInSpace](https://github.com/joreilly/PeopleInSpace) | Koin + KSP 编译期验证、navigation-compose 2.9 多平台接入 |
