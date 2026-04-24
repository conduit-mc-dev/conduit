# Conduit MC — Progress

> 最新更新：2026-04-25
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目决策背景见 [`project-context.md`](./project-context.md)。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [ ] Desktop MVP 迭代 1：端到端联通（配对 + 实例列表）
  - 详细方案见 [`desktop-mvp-plan.md`](./desktop-mvp-plan.md)
  - 架构决策：Koin 4.2（DI）+ JetBrains navigation-compose 2.9（导航）+ shared-core 分层（学 HMCL）

---

## Next（下一步）
- [ ] Desktop MVP 迭代 2-6（创建实例 / server.jar 下载 / 服务器生命周期 / 配置管理 / 实时更新）
- [ ] Loader / Mod 管理里程碑（shared-core 复用层：Daemon + Desktop 启动器共用）
- [ ] Web 管理面板（Compose WasmJS，Daemon 内置 serve）

---

## Done

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
