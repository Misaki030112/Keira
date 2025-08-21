# Keira (Minecraft Fabric Mod)

<p align="center">
  <img src="src/main/resources/assets/keira/icon.png" alt="Keira" width="160"/>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-blue?style=flat-square" alt="English"/></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/语言-简体中文-green?style=flat-square" alt="简体中文"/></a>
  
</p>

<small><em>Note: This project was renamed from "Ausuka.ai" to "Keira".</em></small>

Keira is an in‑game AI assistant for Minecraft — a friendly “Jarvis”-like companion that understands what you say and helps you play, build, explore, and manage your world.

Community server: `114.67.97.163:25565` (Fabric 1.21.8, whitelist on, online‑mode off).

<a href="https://t.me/AusukaMisaki"><img src="https://img.shields.io/badge/Telegram-@AusukaMisaki-27A1E3?logo=telegram&style=flat-square" alt="Telegram"/></a>
<a href="https://weixin.qq.com/"><img src="https://img.shields.io/badge/WeChat-Misaki030112-07C160?logo=wechat&style=flat-square" alt="WeChat"/></a>
<a><img src="https://img.shields.io/badge/Server-114.67.97.163%3A25565-7A39FF?logo=minecraft&style=flat-square" alt="Server 114.67.97.163:25565"/></a>

## Screenshots
<p align="center">
  <img src="docs/images/example1.png" alt="In-game AI chat and actions" width="45%"/>
  <img src="docs/images/example2.png" alt="AI tips, markers, and guidance" width="45%"/>
</p>

## What It Can Do
- Chat naturally and get concise, localized replies.
- Help with survival basics: finding resources, travel and navigation, weather/time, clearing negative effects, quick recovery.
- Assist building and exploration: suggest ideas, mark and recall important locations, offer world context.
- Light server guidance: gentle reminders, tips, and optional moderation aids for admins.

## Quick Start
- Requirements: Fabric 1.21.8, Fabric API, Java 21
- Client (PCL):
  1) Create a Fabric 1.21.8 instance in PCL (PCL2)
  2) Put Fabric API and this mod jar into that instance’s `mods/`
  3) Launch and use `/ai help`, `/ai chat`, or `/ai say <message>`
- Dedicated server:
  1) Install Fabric Loader 1.21.8 and Fabric API
  2) Drop the mod jar into `mods/`
  3) Start with Java 21

## Configure (Short & Simple)
- Set your AI key once. Easiest: create `<.minecraft>/config/keira.properties` and put your key in it.
- Works with mainstream providers. You can also use environment variables or JVM args if you prefer.
- Replies follow each player’s client language; the server language is `en_us`.

## Notes
- Designed to be safe for servers and friendly for players.
- Sessions and memories persist across restarts.
