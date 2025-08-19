<p align="center">
  <img src="src/main/resources/assets/ausuka-ai-mod/icon.png" alt="Ausuka.ai" width="128"/>
</p>

<p align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/Language-English-blue?style=flat-square" alt="English"/></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/语言-简体中文-green?style=flat-square" alt="简体中文"/></a>
</p>

# Ausuka.ai（Fabric Mod）

Ausuka.ai 是 Minecraft 里的 AI 助手，更像你世界里的“贾维斯”。你用自然语言交流，它会理解你的意图，帮助你玩、建、探，也能在需要时给出贴心的提醒。

社区服务器：114.67.97.163:25565（Fabric 1.21.8）。白名单开启，online‑mode 关闭。

<p>
  <a href="https://t.me/AusukaMisaki"><img src="https://img.shields.io/badge/Telegram-@AusukaMisaki-27A1E3?logo=telegram&style=flat-square" alt="Telegram"/></a>
  <a href="https://weixin.qq.com/"><img src="https://img.shields.io/badge/WeChat-Misaki030112-07C160?logo=wechat&style=flat-square" alt="WeChat"/></a>
</p>

## 能力概览（面向玩家）
- 自然语言对话：得到简明、贴近语境的回复（按客户端语言显示）。
- 生存协助：寻找资源、旅行与导航、天气/时间、清理负面效果、快速恢复。
- 建造与探索：灵感建议、标记并记住重要地点（如 “home”“mine”），提供环境/世界上下文。
- 适度管理：适合服主与管理员的温和工具与提醒（不打扰玩家，按需启用）。

## 快速上手
- 前置：Fabric 1.21.8、Fabric API、Java 21
- 客户端（PCL）：
  1) 在 PCL（PCL2）里创建 Fabric 1.21.8 实例
  2) 将 Fabric API 与本 MOD 放入该实例的 `mods/`
  3) 启动后使用 `/ai help`、`/ai chat` 或 `/ai say <内容>`
- 服务端：
  1) 安装 Fabric Loader（1.21.8）并添加 Fabric API
  2) 将本 MOD 放入 `mods/`
  3) 使用 Java 21 启动服务端

## 配置（尽量简单）
- 准备一个 AI Key。最省心的做法：在 `<.minecraft>/config/ausuka-ai-mod.properties` 写入你的 Key。
- 支持常见服务商；也可使用环境变量或 JVM 启动参数按你自己的习惯配置。
- 服务器语言固定为 `en_us`；玩家端收到的信息会按客户端语言显示（zh_cn、en_us、ja_jp 等）。

## 说明
- 以服务器安全为前提进行设计，不打扰、不越权。
- 会话与记忆会在重启后保留。
