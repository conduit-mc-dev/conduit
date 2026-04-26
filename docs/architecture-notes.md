# Architecture Notes

> 记录架构决策、代码组织评估、外部项目借鉴。随项目演进更新。

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
| `Json` 实例分散 | 5+ 处独立创建 `Json`，配置不完全一致。`ModRoutes.kt:55` 用裸 `Json`。应在 shared-core 定义 `ConduitJson` 统一引用 | 中 |
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

**第二步：补充 ModrinthClient 独立测试** — 待实施

文件：`shared-core/src/jvmTest/kotlin/dev/conduit/core/download/ModrinthClientTest.kt`

| 测试用例 | 覆盖点 |
|----------|--------|
| `search returns parsed results with correct field mapping` | search() + snake_case→camelCase 映射 |
| `search builds correct facets with mcVersion and loader` | facets 参数构建 |
| `search coerces limit to 1-100 range` | limit 边界值 |
| `getProjectVersions returns version list` | getProjectVersions() + 响应映射 |
| `getVersion returns single version with file hashes` | getVersion() + ModrinthFileHashes |
| `downloadFile writes content to disk` | 文件写入 + 返回字节数 |
| `API error throws ModrinthApiException` | 4xx/5xx 错误处理 |

Mock 模式：构造函数注入 `HttpClient(MockEngine { ... })`，按 URL path 分发响应。

**第三步：补充 ConduitWsClient 独立测试** — 待实施（触发条件：WsClient 加重连逻辑时）

文件：`shared-core/src/jvmTest/kotlin/dev/conduit/core/api/ConduitWsClientTest.kt`

| 测试用例 | 覆盖点 |
|----------|--------|
| `subscribe sends correct JSON message` | subscribe() 消息格式 |
| `unsubscribe sends correct JSON message` | unsubscribe() 消息格式 |
| `subscribe when session is null does not throw` | null 安全 |
| `incoming text frame is parsed as WsMessage` | 反序列化 |
| `malformed frame does not crash` | 容错处理 |

建议先用方案 B（只测序列化/反序列化），等重连逻辑加入后升级为方案 A（mock WS server）。

**保留在 daemon 中的测试**

- `ConduitApiClientTest` — 需要真实服务端
- 所有 `*RoutesTest.kt` — 测的是 daemon 路由层
- `E2ELifecycleTest` — 全链路测试

---

## HMCL 对比与借鉴（2026-04-26）

> HMCL (Hello Minecraft Launcher) 是成熟的开源 MC 启动器，参考仓库已 clone 到 `~/Documents/HMCL`。

### 根本差异：不宜照搬

| 维度 | HMCL | Conduit MC |
|------|------|-----------|
| 架构 | 单进程桌面应用 | daemon/client 分离 |
| UI-Core 耦合 | HMCLCore 依赖 JavaFX Property | shared-core 零 UI 依赖（KMP） |
| 异步模型 | 自定义 `Task<T>` monad 链 | Kotlin 协程 + TaskStore + WS 广播 |
| 目标 | 启动 MC 客户端 | 管理 MC 服务端 + 未来启动器 |

Conduit 在模块解耦和平台无关性上做得比 HMCL 更好，不需要向其架构靠拢。

### 值得借鉴的 3 个模式

#### 1. DownloadProvider URL 注入（镜像切换）

HMCL 对 Mojang JSON 中所有嵌入 URL 统一做 `injectURL()` 重写，而非仅在下载时判断。

- **当前状态**：`MojangClient` 已借鉴前缀替换模式处理 server.jar URL（`34f766e`）
- **未来需求**：当 desktop 增加客户端启动功能时，需处理 asset index、libraries 等大量嵌入 URL，届时应参考 HMCL 的 `DownloadProvider.injectURL()` 做全局 URL 重写
- **参考文件**：`HMCLCore/.../download/DownloadProvider.java`

#### 2. GameBuilder 组合构建（一步到位安装）

HMCL 用 builder 收集 `(mcVersion, forge=xxx, fabric=xxx)` 后 `buildAsync()` 输出单一复合 Task。

- **当前状态**：Conduit 是"创建实例下载 JAR"和"安装 Loader"两个独立操作，用户需手动分步触发
- **借鉴方向**：`CreateInstanceRequest` 增加可选 `loader` 字段，创建时一步到位完成 JAR 下载 + loader 安装，对外暴露为单一 Task
- **参考文件**：`HMCLCore/.../download/DefaultGameBuilder.java`

#### 3. LibraryAnalyzer 文件推导状态

HMCL 不存储"当前装了什么 loader"，而是分析 version.json 的 library 列表来推导。

- **当前状态**：`InstanceStore` 存储 `LoaderInfo`，如果磁盘上的 JAR 被手动替换/删除，信息会过时
- **借鉴方向**：可在 daemon 启动时或 loader 操作前加一个校验步骤，对比存储信息与实际文件
- **参考文件**：`HMCLCore/.../game/LibraryAnalyzer.java`

### 明确不参考的部分

- **`Task<T>` 异步框架**：Kotlin 协程天然适配 C/S 架构，不需要回退到回调链
- **JavaFX Observable 绑定**：Compose 生态用 `StateFlow` + ViewModel
- **客户端启动逻辑**：natives 解压、asset 下载等与服务端管理无关
- **六种 modpack 格式**：Conduit 只需 `.mrpack`
