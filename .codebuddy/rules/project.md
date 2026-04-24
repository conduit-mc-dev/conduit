---
description: Conduit MC project rules and working agreements
alwaysApply: true
---

# Conduit MC — Project Rules

This file is the single source of truth for project rules and constraints.
It is shared by both CodeBuddy and Claude Code.

## Project identity

- **Name**: Conduit MC
- **GitHub**: https://github.com/conduit-mc-dev/conduit
- **Organization**: `conduit-mc-dev` (GitHub org)
- **License**: GPLv3
- **Status**: Active development — Daemon skeleton complete, Desktop MVP in progress
- **Language of tagline**: Bilingual EN / 简体中文

## What the project is

A **self-hosted Minecraft server manager with a matching launcher**.
Target scenario: the author rents a VPS, wants to run a modded MC server
and invite friends. Today that flow is painful; Conduit MC turns it into
one invite link that syncs mods to every friend's client.

## Architecture (two components)

1. **Daemon** — runs on the VPS; a **server-side launcher** managing multiple MC instances.
   - Manages **multiple independent instances**, each with its own loader, mods, config, and game port.
   - Installs loaders (Forge/NeoForge/Fabric) per instance.
   - Manages mods and the MC server process per instance.
   - Exposes a **private management API** (REST + WebSocket, token-auth) for Desktop.
   - Exposes **public read-only endpoints** (per-instance `server.json` + `.mrpack`) for Desktop.
   - Supports **multiple paired devices** — the same owner can manage from different machines.
   - Serves built-in **Web panel** (WasmJS static files) as a future lightweight management interface.
2. **Desktop** — a unified app for both the server owner and friends.
   - **Server Management**: pairs with Daemon, manages instances, searches Modrinth, builds modpacks, watches console, generates invite links. Only visible when paired.
   - **Game Launcher**: consumes invite links (`conduit://host:port/instanceId`), syncs mods via `.mrpack`, installs loader, launches the game, auto-joins. Always available.
   - Server owners use both; friends primarily use the launcher.

**Key protocol choice**: the server never re-distributes mod jars.
Mods are referenced by URL + hash in a `.mrpack`, and the player's
client downloads them directly from the official CDN (Modrinth / CurseForge).
This keeps us out of re-distribution gray areas.

## Tech stack (hard constraints)

- **Language**: Kotlin (one language for shared-core, Daemon, Desktop, and Web)
- **Desktop UI**: Compose Multiplatform (Desktop target)
- **Web UI** (future): Compose Multiplatform (WasmJS target)
- **Server framework** (Daemon): Ktor (server + client)
- **DI**: Koin 4.2（KMP 全平台，Compose 集成）
- **Desktop navigation**: JetBrains navigation-compose（官方维护，支持 Desktop + WasmJS）
- **Integrations build format**: Modrinth `.mrpack`
- **Auth**: Microsoft OAuth (official Minecraft login)
- **Platforms**: Windows, macOS, Linux (Daemon also Docker-friendly)
- **Build**: Gradle multi-module

Do not propose changes to the stack without a strong reason and without
checking with the user first.

## Planned module layout

```
conduit-mc/
├── shared-core/        # Kotlin Multiplatform (JVM + WasmJS)
│   └── data models, API client, Mojang/Modrinth client, loader install, mrpack, protocol
│       Packages: core/api/, core/download/, core/loader/, core/mod/, core/pack/
├── daemon/             # Kotlin + Ktor, runs on VPS
├── desktop/            # Compose Desktop, unified management + launcher app
│   └── server management, game launcher, mod sync, instance UI
├── web/                # Compose WasmJS (future), management-only web panel
│   └── served by Daemon as built-in static files
└── docs/               # README assets, branding, design notes
```

- `shared-core` is Kotlin Multiplatform from day one (JVM + WasmJS targets),
  so Desktop and Web share API client, data models, and business logic.
- `web` is management-only (no launcher — browsers cannot launch local processes).
- Scaffolded and partially implemented; see `docs/progress.md` for current state.

## What Conduit MC deliberately does NOT do

- ❌ Compete with CurseForge as a mod marketplace.
- ❌ Host a paid cloud service — everything is self-hosted.
- ❌ Support cracked / offline accounts as a headline feature.
- ❌ Replace MCSManager or Pterodactyl — different target user (host+friend flow).
- ❌ Web panel in the MVP (planned as a post-MVP WasmJS management panel, served by Daemon).
- ❌ Target mobile apps in the early versions.

## When in doubt

- See `docs/api-protocol.md` for the full API protocol specification
  (REST endpoints, WebSocket, `server.json` schema, invite link format).
- See `docs/project-context.md` for the full narrative of decisions
  taken so far (why Conduit, why VPS scenario, why not a web panel,
  why Compose Multiplatform, etc.).
- See `docs/progress.md` for the current state of work (now / next / done).
- Ask the maintainer before making a choice that changes direction.
