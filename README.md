<div align="center">

<!-- Logo placeholder -->
<img src="docs/logo.png" alt="Conduit MC" width="140" />

# Conduit MC

**From your server, to your friends' desktops.**
**从你的服务器，到朋友的桌面。**

*A self-hosted Minecraft server manager with a matching launcher.*
*Host on your VPS, invite friends with one link — mods and all.*

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)]()
[![Status](https://img.shields.io/badge/status-early%20development-orange)]()
[![Made with Kotlin](https://img.shields.io/badge/made%20with-Kotlin-7f52ff.svg)](https://kotlinlang.org/)

</div>

---

## 🇬🇧 English

### The problem

You rented a VPS. You want to run a modded Minecraft server and play with a few friends.

Today, that means:

- SSH into the VPS, install Java, Forge/NeoForge, copy mods over.
- Tell every friend the exact Minecraft version, the loader version, and the list of mods.
- When a friend's client doesn't match, they crash. You debug it over chat.
- You want to add a mod next week. Everyone has to manually update.
- Every single time.

**Conduit MC turns all of that into one link.**

### What Conduit MC does

Conduit MC is three things that talk to each other:

1. **Daemon** — runs on your VPS. Installs loaders, manages mods, starts/stops the Minecraft server, exposes a small public endpoint.
2. **Host Desktop** — your management app. Search Modrinth, build your modpack, watch the console, generate invite links.
3. **Client Desktop** — what your friends install. One click on an invite link, everything syncs automatically, game launches, server joins.

```
┌─────────────────┐         ┌─────────────────┐
│  Host Desktop   │◄───────►│     Daemon      │
│  (you, at home) │  API    │   (on the VPS)  │
└─────────────────┘         └────────┬────────┘
                                     │
                                     │ public endpoint
                                     │ (server.json + .mrpack)
                                     ▼
                            ┌─────────────────┐
                            │ Client Desktop  │
                            │   (friends)     │
                            └─────────────────┘
```

### Why another Minecraft launcher?

There are many great Minecraft launchers (HMCL, Prism, PCL2, Modrinth App).
There are many great server panels (MCSManager, Pterodactyl, Crafty).

**None of them talk to each other.**

Conduit MC exists because:

- **Server admins and players use different tools today**, leading to version mismatches and painful onboarding.
- **The `.mrpack` format is a beautiful standard** — Conduit MC makes it the glue between your server and every friend's client.
- **Self-hosted should feel as easy as Minecraft Realms**, without giving up control of your world.

### Features

- 🚀 **One-link server join** — friends click once, everything syncs
- 📦 **Modrinth-first modpack management** — search, add, update mods from the host desktop
- 🔄 **Automatic client-server sync** — if you add a mod, everyone gets it next launch
- 🖥️ **Remote server control** — manage your VPS server from any desktop
- 🔐 **Microsoft OAuth** — proper account support, no login servers
- 🌐 **Cross-platform** — Windows, macOS, Linux for both host and client
- 🆓 **Free and open source** — GPLv3, forever

### Non-features (by design)

We deliberately do **not** try to:

- ❌ Compete with CurseForge as a mod marketplace — we use their API respectfully, and Modrinth as our primary source
- ❌ Build a hosted cloud service — your server, your rules, your data
- ❌ Support offline/cracked accounts as a headline feature — the project is safer without it
- ❌ Replace MCSManager or Pterodactyl — they're great at what they do, we focus on the host+friend experience

### Tech stack

- **Language:** Kotlin (100% shared logic across all components)
- **UI:** Compose Multiplatform (Desktop)
- **Server framework:** Ktor (Daemon)
- **Modpack format:** [Modrinth `.mrpack`](https://docs.modrinth.com/docs/modpacks/format_definition/) (standard, no vendor lock-in)

### Status

**🚧 Early development.** Nothing runs yet. Follow this repo to see it come to life.

### Roadmap

- [ ] **v0.1** — Daemon + Host Desktop MVP, local only
- [ ] **v0.2** — Client Desktop, invite link flow
- [ ] **v0.3** — Modrinth mod search & management in Host Desktop
- [ ] **v0.4** — Read-only web dashboard (mobile-friendly)
- [ ] **v0.5** — HTTPS auto-cert, backups, multi-instance
- [ ] **v1.0** — Stable API, docs, plugin surface

### Contributing

Contributions are welcome once the initial architecture lands. For now, the best way to help is to open an issue describing your use case — real stories shape what we build.

### License

[GPLv3](./LICENSE) — free to use, modify, and redistribute, but modifications must stay open.

### Inspired by

Standing on the shoulders of giants:
- [HMCL](https://hmcl.huangyuhui.net/) — the gold standard for Minecraft launchers
- [Prism Launcher](https://prismlauncher.org/) — the client that shows how mod management should feel
- [Modrinth](https://modrinth.com/) — for building the `.mrpack` ecosystem
- [MCSManager](https://mcsmanager.com/) — for proving self-hosted panels can be friendly

---

## 🇨🇳 中文

### 一句话说明

**Conduit MC** 是一个自托管的 Minecraft 服务器管理工具，外加一个配套启动器——服主在 VPS 上开服，朋友点一下链接就能装好模组、进服开玩。

### 它解决什么问题

你租了一台 VPS，想和几个朋友一起玩模组服。

今天的流程是：

- SSH 上去装 Java、Forge/NeoForge、一个个传模组
- 告诉每个朋友：什么 MC 版本、什么加载器、装哪些 mod
- 谁版本不对谁崩溃，你在群里一个个 debug
- 下周想加个 mod？大家都得手动更新
- **每次都这样**

**Conduit MC 把这一切变成一条链接。**

### 它由三部分组成

| 组件 | 跑在哪 | 干什么 |
|---|---|---|
| **Daemon** | 你的 VPS | 装加载器、管模组、起服务端、对外暴露一个小接口 |
| **Host Desktop** | 你的电脑 | 搜 Modrinth 模组、搭整合包、看控制台、生成邀请链接 |
| **Client Desktop** | 朋友的电脑 | 点链接 → 自动同步模组 → 启动游戏 → 自动进服 |

### 为什么再做一个 MC 工具？

- **客户端启动器和服务器面板从来不说话** —— 这导致版本错位和糟糕的加入体验
- **Modrinth 的 `.mrpack` 格式已经是标准** —— Conduit MC 让它成为服主和朋友之间的粘合剂
- **自托管应该像 Minecraft Realms 一样简单** —— 同时不失去对世界的控制权

### 技术选型

- **Kotlin** 一套语言贯穿所有组件
- **Compose Multiplatform** 跨桌面 UI
- **Ktor** 作为 Daemon 的服务框架
- **Modrinth `.mrpack`** 作为整合包标准格式

### 当前状态

**🚧 早期开发中。** 还没东西能跑。关注本仓库见证它诞生。

### 开发路线

- [ ] **v0.1** — Daemon + Host Desktop MVP，支持本机
- [ ] **v0.2** — Client Desktop 玩家端、邀请链接全流程
- [ ] **v0.3** — Host Desktop 接入 Modrinth 模组搜索/管理
- [ ] **v0.4** — 只读 Web 仪表盘（手机浏览器可看）
- [ ] **v0.5** — HTTPS 自动证书、备份、多实例
- [ ] **v1.0** — 稳定 API、文档、插件接入

### 开源协议

[GPLv3](./LICENSE) —— 自由使用、修改、分发，但修改必须保持开源。

### 致谢

站在巨人肩上：
- [HMCL](https://hmcl.huangyuhui.net/) —— Minecraft 启动器的黄金标准
- [Prism Launcher](https://prismlauncher.org/) —— 示范了模组管理该有的样子
- [Modrinth](https://modrinth.com/) —— 建立了 `.mrpack` 生态
- [MCSManager](https://mcsmanager.com/) —— 证明了自托管面板可以友好易用

---

<div align="center">

**Made with ❤️ for Minecraft hosts and their friends.**

**献给每一位模组服服主和他们的朋友。**

</div>
