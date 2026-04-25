# Conduit MC — Progress

> 最新更新：2026-04-26
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目约束见根目录 `CLAUDE.md`。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [ ] Daemon 已实现端点与 `api-protocol.md` 规范对齐（11 处偏差修正）
- [ ] Daemon 未实现端点补全（37 个，Loader / Mod / Pack / 配置 / 文件管理 / 公开端点等）

---

## Next（下一步）

- [ ] Daemon 全端点自动化测试覆盖
- [ ] Desktop MVP 迭代 4-6（Daemon 稳定后启动，方案见 `desktop-mvp-plan.md`）

---

## Done

- [x] Desktop MVP 迭代 1-3（配对 / 创建实例 / 服务器生命周期）
  - 详细记录见 git log（`8817c9d`、`7f168e0`、`fcedad8`）
  - 自动化测试 35 个
- [x] Daemon 骨架（Ktor + 配对流程 + 实例 CRUD + 服务器生命周期）
- [x] Gradle 多模块脚手架（shared-core / daemon / desktop / web）
- [x] API 协议规范（`docs/api-protocol.md`）
- [x] 项目定位、技术栈选型、命名、Logo、README、LICENSE
- [x] 文档整合：CLAUDE.md 内联所有约束，移除 .codebuddy 多层引用
