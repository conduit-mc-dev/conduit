# Conduit MC — Project Context

> **这份文档是"前情提要"**。如果你（未来的 CodeBuddy / 贡献者 / 未来的自己）刚加入 Conduit MC 项目，从头读完这份文档，就能理解我们为什么做这些决定、走到了哪一步、下一步要往哪里去。
>
> 更新时间：2026-04-24

---

## 一、项目一句话定位

**Conduit MC** 是一个**自托管 Minecraft 服务器管理工具 + 配套双端启动器**。
服主在 VPS 上开模组服，朋友点一下邀请链接就能同步模组、启动游戏、加入服务器。

---

## 二、这个项目解决什么问题

作者（也是第一用户）自己的使用场景：

> 租了一台 VPS，想和几个朋友玩模组 MC。
> 每次都要 SSH 上去装 Java、装 Forge/NeoForge、一个个传 mod，
> 然后告诉每个朋友版本号、加载器、模组清单，
> 谁装错版本谁崩溃，就在群里一个个 debug。
> 下周想加一个 mod？所有人都要手动更新。

现有工具链**把这两件事隔开了**：
- **客户端启动器**（HMCL / Prism / PCL2）只管玩家本地
- **服务器面板**（MCSManager / Pterodactyl）只管服务端
- **它们从来不说话**

Conduit MC 的核心价值就是 **让这两端对话**：服主管理模组时，玩家的客户端自动跟上。

---

## 三、核心架构（两个组件）

```
┌─────────────────┐         ┌─────────────────┐
│    Desktop      │◄───────►│     Daemon      │
│  (服主 + 朋友)   │  API    │   (在 VPS 上)   │
│                 │◄────────│                 │
│  管理 + 启动器   │ 公开端点 │  未来内置 Web   │
└─────────────────┘         └─────────────────┘
```

| 组件 | 跑在哪 | 职责 |
|---|---|---|
| **Daemon** | VPS（Linux，可选 Docker） | 装加载器、管 mod、起 MC 进程、对外暴露 API；未来内置 Web 管理面板 |
| **Desktop** | 服主和朋友的电脑（Win/macOS/Linux） | 统一应用：**服务器管理**（配对后可用）+ **游戏启动器**（始终可用） |

> 早期设计中 Desktop 曾拆分为 Host Desktop（管理端）和 Client Desktop（玩家端）。
> 后来确认服主自己也要玩游戏，且两端技术栈完全一致，遂合并为一个应用。

**两条通信通道**（不变）：

1. **管理 API**（Desktop 管理功能 ↔ Daemon）
   - REST + WebSocket
   - 带 token 鉴权（首次配对生成）
   - 操作服务器、管理 mod、看控制台

2. **公开只读端点**（Desktop 启动器功能 ↔ Daemon）
   - 纯 HTTPS GET
   - 拿 `server.json`（服务器信息）和 `pack.mrpack`（整合包清单）
   - 无鉴权，但只读，不暴露管理能力

**未来 Web 端**：Daemon 内置 serve WasmJS 静态文件，提供轻量管理面板。
浏览器无法启动本地进程，因此 Web 端只做管理，不做启动器。

**玩家客户端 → MC 服务端**的游戏流量是原生 MC 协议，**不经过 Conduit**——我们只负责"配套"，不碰游戏网络。

---

## 四、关键设计决策（记录"为什么"）

### 1. 为什么是"VPS + Daemon"而不是"本机开服 + Desktop"？

作者的真实场景就是 VPS。朋友要随时进服，作者不可能 24 小时开电脑。
这决定了：
- **必须有 Daemon**（headless、跑在远端）
- **Desktop 的管理功能是遥控器**，不是"本机服务端"
- 本机开服场景依然支持（Daemon 可以跑在 localhost），但不是主场景

### 2. 为什么 Desktop 优先、Web 后做？

MCSManager 和 Pterodactyl 都是 Web 面板，但：
- 我们的核心卖点是**客户端同步 + 启动游戏**，这需要桌面 App 才能做（浏览器无法启动本地进程）
- Web 只能做管理面板，做不了启动器——Desktop 是产品差异化的关键
- 未来 Web 端（Compose WasmJS）由 Daemon 内置 serve，作为**轻量管理补充**
- `shared-core` 使用 Kotlin Multiplatform（JVM + WasmJS），Desktop 和 Web 共享 API 客户端和数据模型

### 3. 为什么选 Kotlin + Compose Multiplatform？

- **Kotlin + JVM 生态 = Minecraft 原生生态**。authlib、Forge installer、mrpack 解析库一大堆现成的
- **一套语言写所有模块**（shared-core / daemon / desktop / web），认知负担最低
- **Compose Multiplatform** 桌面端已经稳定，WasmJS target 可覆盖未来 Web 端
- 不选 Tauri/Rust：调用 Java 启动器逻辑需要额外启动 JVM 子进程，架构上不自然
- 不选 Electron：打包体积过大，启动器用户群体对此较为敏感

### 4. 为什么 `.mrpack` 是核心格式？

- Modrinth 的官方整合包格式
- **只存元数据 + 官方 CDN URL**，模组 jar 不进我们的分发链路
- Prism / HMCL / Modrinth App 都原生支持，玩家启动器生态兼容
- **合规性最高**：我们从不 host 任何第三方 mod 文件

### 5. 为什么 GPLv3？

- HMCL / Prism / PCL 都用 GPLv3，MC 启动器社区惯例
- 防止被商业公司闭源 fork
- AGPLv3 限制过严（若未来有人希望进行私有 SaaS 部署，AGPL 会构成障碍）
- MIT 保护不足（存在被闭源 fork 的风险）

### 6. 为什么名字是 Conduit MC？

命名经过多轮筛选，简要记录：

1. **YAMCL**（Yet Another MC Launcher）—— 致敬梗，但缩写辨识度弱，已被 2014 年停更项目占用
2. **Beacon** —— 撞 Ark 服务器管理器 `usebeacon.app`
3. **Lodestone** —— 撞 Lodestone-Team（1.3k star 的直接竞品）
4. **Tavern** —— 撞 SillyTavern（AI 角色扮演工具，中文圈"酒馆"已是它的）
5. **Taverncraft** —— 撞多个 MC 服务器、整合包
6. **Conduit** ✓ —— MC 里的潮涌核心方块，语义契合"连接两端"
   - 虽然有 `Conduit.io` / `Matrix Conduit` / `Conduit.xyz` 等同名品牌，但都不在 MC 领域
   - 加 `MC` 后缀（`Conduit MC`）可以和他们明确区分
   - GitHub 组织名用 `conduit-mc-dev` 避免和 `ConduitMC`（已归档的老 MC 项目）冲突

**定位表达**：
- 项目名：**Conduit MC**
- GitHub：`conduit-mc-dev/conduit`
- Slogan（英）：*From your server, to your friends' desktops.*
- Slogan（中）：**从你的服务器，到朋友的桌面。**

### 7. 为什么不做 CurseForge 集成（MVP 阶段）？

- CurseForge API 需要 API Key 申请，有审批门槛
- 很多模组作者勾了 `allowDistribution=false`，第三方工具拿不到下载链接
- Modrinth 已经覆盖了绝大多数需要的 mod，且政策宽松
- MVP 不做 CurseForge，v0.5+ 再考虑加

---

## 五、不做什么（明确的 non-goals）

明确边界与确定方向同样重要：

- ❌ **不做 CurseForge 模组浏览器**——走 Modrinth，避开 API 审批 + 再分发风险
- ❌ **不做付费云服务**——完全自托管
- ❌ **不做破解/盗版支持**——Microsoft OAuth 为唯一路径（外置登录可能 v1.0 后支持）
- ❌ **不做 MCSManager/Pterodactyl 的替代**——目标用户不同（他们面向专业服主/托管商，我们面向"开服邀请朋友"的个人）
- ❌ **不在 MVP 做 Web 面板**——post-MVP 用 Compose WasmJS 实现，Daemon 内置 serve
- ❌ **不做原生移动 App（初期）**
- ❌ **不做整合包社区/市场**——Modrinth 已经是，不重复造

---

## 六、命名 / 组织历史（方便将来回查）

- GitHub 组织：`conduit-mc-dev`
- GitHub 仓库：`conduit-mc-dev/conduit`（public）
- 默认分支：`main`
- License：GPLv3

---

## 七、重要的"已讨论但暂不做"清单

以下话题已讨论过但暂时推迟，记录在此以备后续参考：

| 话题 | 现状 | 何时再议 |
|---|---|---|
| 移动 App（Host 端） | 不做 | v0.5+ 视用户呼声 |
| Web 面板（WasmJS 管理端） | Daemon 内置 serve，post-MVP | v0.4 规划时 |
| 远程 daemon 多租户 | 不做 | 永远不做（违反产品定位） |
| CurseForge 模组源 | 不做 | v0.5+ 看需要 |
| Bedrock Edition 支持 | 不做 | 长期不做（核心是 Java 版模组服） |
| Geyser（跨版本联机） | 不做 | 长期看情况 |
| Authlib-injector（外置登录） | 初期不做 | v1.0 后考虑 |
| 自建中继 / 穿透服务 | 不做 | 用户自己用 Tailscale / frp |
| 插件系统 | 不做 | v1.0 后 |

---

## 八、为未来的"接棒者"

如果你是未来的 AI 助手（新对话加入）或者未来的贡献者：

- **先读这份文档**，再读 README
- 想看当前进度：[`docs/progress.md`](./progress.md)
- 读完还有疑问，翻 git log（commit message 写得还算详细）
- 有两份文件互补：
  - `.codebuddy/rules/project.md` —— AI 工具的执行约束（单一真相源）
  - `docs/project-context.md` —— 这份，给人的叙事背景

项目尚处早期，一切仍有调整空间——但上述每个决策都有其充分的理由，修改前请先理解其背景。

---

*"From your server, to your friends' desktops."*
*"从你的服务器，到朋友的桌面。"*
