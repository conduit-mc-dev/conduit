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

## 编码准则

减少 AI 编码常见错误的行为准则。偏向谨慎而非速度，简单任务可自行判断。

### 1. 先思考再动手

**不要假设，不要隐藏困惑，主动暴露权衡。**

- 明确陈述你的假设。不确定就问。
- 如果存在多种理解，列出来让用户选——不要自己默默选一个。
- 如果有更简单的方案，说出来。该反驳时就反驳。
- 如果有不清楚的地方，停下来，指出困惑点，然后问。

### 2. 简洁优先

**用最少的代码解决问题，不做任何投机性编码。**

- 不加用户没要求的功能。
- 不为只用一次的代码创建抽象。
- 不加没被要求的"灵活性"或"可配置性"。
- 不为不可能的场景做错误处理。
- 如果写了 200 行但 50 行能搞定，重写。

自检：高级工程师会觉得这段代码过度复杂吗？如果会，精简它。

### 3. 精准修改

**只动该动的，只清理自己造成的。**

编辑现有代码时：
- 不要"顺手改进"周围的代码、注释或格式。
- 不要重构没坏的东西。
- 匹配现有风格，即使你会用不同的写法。
- 如果发现不相关的死代码，提一嘴——但不要删。

当你的改动造成了孤立代码时：
- 删除**你的改动**导致不再使用的 import / 变量 / 函数。
- 不要删除改动之前就已存在的死代码，除非被要求。

检验标准：每一行改动都应直接追溯到用户的请求。

### 4. 目标驱动执行

**定义成功标准，循环验证直到达成。**

把任务转化为可验证的目标：
- "加验证" → "为无效输入写测试，然后让测试通过"
- "修 bug" → "写一个能复现 bug 的测试，然后让它通过"
- "重构 X" → "确保重构前后测试都通过"

多步任务时，先列简要计划：
```
1. [步骤] → 验证：[检查点]
2. [步骤] → 验证：[检查点]
3. [步骤] → 验证：[检查点]
```

### 5. 唯一信息源

**项目知识存仓库，不存 AI 工具的本地记忆。**

- 唯一存储位置是仓库中的项目文档（`docs/`、`CLAUDE.md` 等）。
- 不要在 AI 工具的本地存储中重复保存项目文档已有的信息。
- 新 session 通过 `CLAUDE.md` → `docs/progress.md` → 具体文档的引导链找到上下文。

### 6. 规范即验收标准

**实现 API 端点时，以 `docs/api-protocol.md` 为准，逐字段核对。**

- 实现前：读对应章节，确认请求/响应结构、HTTP 状态码、错误码。
- 实现后：逐字段核对响应 body 和错误场景，不能凭记忆假设"应该差不多"。
- 如需偏离规范：先提出并更新文档，不要默默改实现。

## 关键参考文档

- [`docs/api-protocol.md`](docs/api-protocol.md) — API 协议规范（REST + WebSocket + 错误码，**实现的唯一标准**）
- [`docs/progress.md`](docs/progress.md) — 当前进度与下一步计划

## 当前开发阶段

**Daemon 优先**。先完成 Daemon 所有 API 端点并通过自动化测试，再推进 Desktop。
