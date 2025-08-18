# Ausuka.ai (Fabric Mod)

---

## Overview
Ausuka.ai is a Fabric mod that brings an AI chat assistant to Minecraft. Use natural language to get items, teleport, control environment, or ask for contextual tips. It supports i18n, admin controls, and persistent storage.

## Highlights
- Smart chat and one-shot Q&A: `/ai chat`, `/ai say <message>`.
- Conversation and memory: `/ai new` resets context; conversations and locations are persisted.
- Auto messages: scheduled broadcast and personal tips (admin-controllable).
- Admin commands: `/ai admin auto-msg ...`, `/ai admin stats` for system stats.
- Unified permissions: USER / MOD_ADMIN / SERVER_ADMIN (OP/SP is SERVER_ADMIN).
- DB-backed sessions: AI chat mode state survives restarts.
- I18N: auto language selection (zh_cn / en_us).

## Quick Start
- Basics: `/ai help`, `/ai chat`, `/ai say <message>`, `/ai exit`, `/ai new`, `/ai status`.
- Admin: `/ai admin auto-msg toggle|status|personal <player> <on|off>`, `/ai admin stats`.

## Tech Stack
- Fabric 1.21.8, Java 21.
- Spring AI Client Chat + DeepSeek (single-call + tool calls).
- MyBatis 3 + H2 (persistent sessions, conversations, and locations).
- Async handling with main-thread dispatch for safety.
- Mod ID: `ausuka-ai-mod`

## Install & Config
- Requires Fabric Loader, Fabric API, Java 21.
- Provide an API key via one of the following (priority order):
  1) JVM arg: `-DDEEPSEEK_API_KEY=...`
  2) Env var: `DEEPSEEK_API_KEY=...`
  3) Config file: create `<.minecraft>/config/ausuka-ai-mod.properties` with:
     - `DEEPSEEK_API_KEY=your_key_here`
     - Optional: `AI_PROVIDER=deepseek`

- Windows (single-player): using a config file is recommended because the Minecraft Launcher may not pass env vars to the game. Create `%APPDATA%/.minecraft/config/ausuka-ai-mod.properties` and put your key there.

- Drop the built jar into `mods/` and launch.
