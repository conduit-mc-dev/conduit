# Conduit MC — Progress

> 最新更新：2026-04-20
> 版本里程碑（v0.1 / v0.2 / ...）见 [README Roadmap](../README.md#roadmap)。
> 项目决策背景见 [`project-context.md`](./project-context.md)。

这份文档记录**当前细颗粒进度**：正在做什么、下一步做什么、已经做完什么。
每次有实质推进时更新。

---

## Now（进行中）

- [ ] **API 协议规范**（`docs/api-protocol.md`）
  - Host ↔ Daemon REST + WebSocket 设计
  - Client ↔ Daemon 公开端点设计
  - `server.json` schema
  - 邀请链接格式
  - pair token 流程

---

## Next（下一步）

- [ ] Gradle 多模块脚手架（`shared-core` / `daemon` / `host-desktop` / `client-desktop`）
- [ ] Daemon 骨架（Ktor + 配对流程 + 第一个接口）
- [ ] Host Desktop MVP（最小可用管理 UI）
- [ ] Client Desktop MVP（邀请链接入服全流程）

---

## Done

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
