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
- **Status**: Early development — nothing runs yet
- **Language of tagline**: Bilingual EN / 简体中文

## What the project is

A **self-hosted Minecraft server manager with a matching launcher**.
Target scenario: the author rents a VPS, wants to run a modded MC server
and invite friends. Today that flow is painful; Conduit MC turns it into
one invite link that syncs mods to every friend's client.

## Architecture (three components)

1. **Daemon** — runs on the VPS.
   - Installs loaders (Forge/NeoForge/Fabric).
   - Manages mods and the MC server process.
   - Exposes a **private management API** (REST + WebSocket, token-auth) for Host Desktop.
   - Exposes a **public read-only endpoint** (`server.json` + `.mrpack`) for Client Desktop.
2. **Host Desktop** — the admin/owner's app.
   - Connects to the Daemon.
   - Searches Modrinth, builds modpacks, watches console, generates invite links.
3. **Client Desktop** — the friends' app.
   - Consumes invite links.
   - Syncs mods via `.mrpack`, installs loader, launches the game, auto-joins.

**Key protocol choice**: the server never re-distributes mod jars.
Mods are referenced by URL + hash in a `.mrpack`, and the player's
client downloads them directly from the official CDN (Modrinth / CurseForge).
This keeps us out of re-distribution gray areas.

## Tech stack (hard constraints)

- **Language**: Kotlin (one language for shared-core, Daemon, and both Desktops)
- **Desktop UI**: Compose Multiplatform
- **Server framework** (Daemon): Ktor
- **Integrations build format**: Modrinth `.mrpack`
- **Auth**: Microsoft OAuth (official Minecraft login)
- **Platforms**: Windows, macOS, Linux (Daemon also Docker-friendly)
- **Build**: Gradle multi-module

Do not propose changes to the stack without a strong reason and without
checking with the user first.

## Planned module layout

```
conduit-mc/
├── shared-core/        # Kotlin, shared business logic
│   └── auth, mrpack, modrinth client, loader install, launch, protocol
├── daemon/             # Kotlin + Ktor, runs on VPS
├── host-desktop/       # Compose Desktop, admin app
├── client-desktop/     # Compose Desktop, friends app
└── docs/               # README assets, branding, design notes
```

(Not yet scaffolded — this is the target layout.)

## What Conduit MC deliberately does NOT do

- ❌ Compete with CurseForge as a mod marketplace.
- ❌ Host a paid cloud service — everything is self-hosted.
- ❌ Support cracked / offline accounts as a headline feature.
- ❌ Replace MCSManager or Pterodactyl — different target user (host+friend flow).
- ❌ Offer a web panel in the MVP (can add later as read-only).
- ❌ Target mobile apps in the early versions (PWA / web is fallback).

## When in doubt

- See `docs/project-context.md` for the full narrative of decisions
  taken so far (why Conduit, why VPS scenario, why not a web panel,
  why Compose Multiplatform, etc.).
- See `docs/progress.md` for the current state of work (now / next / done).
- Ask the maintainer before making a choice that changes direction.
