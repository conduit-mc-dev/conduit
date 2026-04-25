# Conduit MC — Progress

> 最新更新：2026-04-26
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目约束见根目录 `CLAUDE.md`。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [ ] Daemon 未实现端点补全（~34 个，Loader / Mod / Pack / 配置 / 文件管理 / 公开端点等）

---

## Next（下一步）

- [ ] Daemon 全端点自动化测试覆盖
- [ ] Desktop MVP 迭代 4-6（Daemon 稳定后启动，方案见 `desktop-mvp-plan.md`）

---

## Done

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
