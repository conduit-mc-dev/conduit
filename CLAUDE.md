# Conduit MC — Project Instructions

本文件是 AI 工具（Claude Code）的唯一入口文档。所有执行约束在此内联，不依赖多层引用。

## 项目定位

- **Conduit MC**：自托管 Minecraft 服务器管理工具 + 配套启动器
- 服主在 VPS 上开模组服，朋友点邀请链接就能同步模组、启动游戏、加入服务器
- GitHub: `conduit-mc-dev/conduit` | License: GPLv3

## 架构

1. **Daemon** — 跑在 VPS 上，管理多个 MC 实例（安装加载器、管 mod、起进程、暴露 API）
2. **Desktop** — 服主和朋友的电脑上，统一应用：服务器管理（配对后可用）+ 游戏启动器（始终可用）
3. **Web**（future）— Compose WasmJS 管理面板，Daemon 内置 serve
4. **shared-core** — Kotlin Multiplatform（JVM + WasmJS），Desktop/Web/Daemon 共享数据模型和 API 客户端

## 技术栈（硬约束，变更需先讨论）

- **语言**: Kotlin（全模块统一）
- **Desktop UI**: Compose Multiplatform (Desktop target)
- **Web UI** (future): Compose Multiplatform (WasmJS target)
- **Server framework** (Daemon): Ktor
- **DI**: Koin 4.2
- **Desktop 导航**: JetBrains navigation-compose
- **整合包格式**: Modrinth `.mrpack`
- **Auth**: Microsoft OAuth
- **构建**: Gradle 多模块

## 模块布局

```
conduit-mc/
├── shared-core/        # KMP (JVM + WasmJS) — 数据模型、API 客户端、业务逻辑
├── daemon/             # Ktor 服务器，跑在 VPS
├── desktop/            # Compose Desktop，管理 + 启动器
├── web/                # Compose WasmJS（future）
└── docs/               # 设计文档、品牌资产
```

## Non-goals

- ❌ 不做 CurseForge 浏览器（MVP 阶段）
- ❌ 不做付费云服务（完全自托管）
- ❌ 不做破解/盗版支持
- ❌ 不做 MCSManager/Pterodactyl 替代（不同目标用户）
- ❌ MVP 不做 Web 面板
- ❌ 不做原生移动 App（初期）

## 技能框架

本工程启用 6 个 Claude Code 插件（配置见 `.claude/settings.json`）。

**核心两个 — 决定 AI 的姿态与工作流：**

- **`andrej-karpathy-skills`** — 4 条通用行为准则（Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution），在写/审/重构代码时由 skill 自动加载。
- **`superpowers`** — 具体工作流 skill 集合（brainstorming、systematic-debugging、test-driven-development、writing-plans / executing-plans、requesting / receiving-code-review、verification-before-completion 等）。按任务类型自动触发。

**辅助四个 — 在对应场景自动生效：**

- **`episodic-memory`** — 跨 session 的对话语义检索。当你说"上次怎么搞的"或 Claude 判定历史经验相关时，自动搜 `~/.claude/projects/` 下的过往对话。
- **`elements-of-style`** — 写文档、commit message、错误信息、PR 描述时，自动套 Strunk《The Elements of Style》的简洁写作规则。
- **`claude-session-driver`** — 通过 tmux 启动并监控 worker Claude 会话，支持并行任务拆分（配合 `superpowers:dispatching-parallel-agents`）。
- **`double-shot-latte`** — Claude 即将 stop 时评估是否真的完工；未完则续跑。防过早 stop，与 `superpowers:verification-before-completion` 组成双保险。

首次克隆后 Claude Code 会提示信任两个 marketplace（`superpowers-marketplace`、`karpathy-skills`），批准一次后全部 6 个插件激活。通用行为准则由 skill 提供，**CLAUDE.md 只保留 skill 不覆盖的工程特有条款**。

## 规范即验收标准

**实现 API 端点时，以 `docs/api-protocol.md` 为准，逐字段核对。**

- 实现前：读对应章节，确认请求/响应结构、HTTP 状态码、错误码。
- 实现后：逐字段核对响应 body 和错误场景，不能凭记忆假设"应该差不多"。
- 如需偏离规范：先提出并更新文档，不要默默改实现。

## 关键参考文档

- [`docs/api-protocol.md`](docs/api-protocol.md) — API 协议规范（REST + WebSocket + 错误码，**实现的唯一标准**）
- [`docs/progress.md`](docs/progress.md) — 当前进度与下一步计划
- [`docs/architecture-notes.md`](docs/architecture-notes.md) — 架构决策、代码组织评估、HMCL 借鉴点

## 当前开发阶段

**Daemon 优先**。先完成 Daemon 所有 API 端点并通过自动化测试，再推进 Desktop。
