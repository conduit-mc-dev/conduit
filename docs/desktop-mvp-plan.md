# Desktop MVP 方案

> 创建于 2026-04-25 | 更新于 2026-04-26
> 状态：**暂停** — 当前优先完成 Daemon，Desktop 待 Daemon 稳定后再推进

## 架构决策（仍然有效）

### 1. shared-core 分层

`shared-core` 承载所有可复用的业务逻辑（不仅是数据模型）：
- `core/api/` — Daemon HTTP API 客户端 + WebSocket 客户端
- `core/download/` — Mojang 版本清单解析、server.jar/client.jar 下载
- `core/loader/` — Fabric/Forge/NeoForge 安装逻辑（Daemon + Desktop 共用）
- `core/mod/` — Modrinth API 客户端、mod 文件管理
- `core/pack/` — mrpack 打包/解析

Daemon 是 shared-core 的 HTTP 包装层。Desktop 管理模式通过 API Client 远程调用 Daemon，启动器模式直接调用 shared-core 本地执行。

### 2. DI → Koin 4.2

KMP 生态事实标准，Compose 集成（`koinViewModel()`）。

### 3. 导航 → JetBrains navigation-compose 2.9

官方维护，支持 Desktop + WasmJS，类型安全路由。

### 4. shared-core API Client

`shared-core/src/jvmMain/` 的 `ConduitApiClient`（Ktor Client + CIO）。Web 面板时提升到 `commonMain` + expect/actual 引擎。

## 已完成的迭代（迭代 1-3）

| 迭代 | 内容 | 状态 |
|---|---|---|
| 1 | 端到端联通：配对 + 实例列表 | ✅ |
| 2 | 创建实例 + server.jar 下载 | ✅ |
| 3 | 服务器生命周期（启动/停止/控制台） | ✅ |

## 待做的迭代（Daemon 稳定后启动）

| 迭代 | 内容 |
|---|---|
| 4 | server.properties 读写 + 实例删除 + 玩家列表 + UI 打磨 |
| 5 | WebSocket 实时更新 + 下载进度条 + TPS/内存统计 |
| 6 | JVM 配置 + 重启 + 实例编辑 + 整体打磨 |

迭代 4-6 完成后进入 Loader / Mod 管理里程碑。

## 参考仓库

| 仓库 | 参考重点 |
|---|---|
| [HMCL](https://github.com/HMCL-dev/HMCL) | Mojang API、Modrinth 客户端、loader 安装 |
| [OctoMeter](https://github.com/ryanw-mobile/OctoMeter) | 导航模式、Koin 配置 |
| [PeopleInSpace](https://github.com/joreilly/PeopleInSpace) | Koin + KSP、navigation-compose 多平台 |
