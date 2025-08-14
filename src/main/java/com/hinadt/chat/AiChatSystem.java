package com.hinadt.chat;

import com.hinadt.AiMisakiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.ai.AiWorkflowManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * AI Chat Command System - AI驱动的聊天命令系统
 * 提供进入/退出AI聊天模式的功能
 */
public class AiChatSystem {
    
    private static MinecraftServer server;
    private static final Set<String> aiChatPlayers = ConcurrentHashMap.newKeySet();
    private static AiWorkflowManager workflowManager;
    
    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        workflowManager = new AiWorkflowManager(server);
        
        registerCommands();
        registerChatListener();
        
        AiMisakiMod.LOGGER.info("AI聊天系统初始化完成！");
    }
    
    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("chat")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        String playerName = player.getName().getString();
                        if (aiChatPlayers.contains(playerName)) {
                            player.sendMessage(Text.of("§c[AI Misaki] 你已经在AI聊天模式中了！使用 /ai exit 退出"));
                            return 0;
                        }
                        
                        aiChatPlayers.add(playerName);
                        player.sendMessage(Text.of("§b[AI Misaki] §a✨ 欢迎进入AI聊天模式！"));
                        player.sendMessage(Text.of("§b[AI Misaki] §f现在你可以直接和我对话，我会理解你的需求并提供帮助"));
                        player.sendMessage(Text.of("§b[AI Misaki] §f使用 /ai exit 退出AI聊天模式"));
                        
                        // 发送AI欢迎消息
                        sendAiWelcomeMessage(player);
                        
                        return 1;
                    }))
                .then(CommandManager.literal("exit")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        String playerName = player.getName().getString();
                        if (!aiChatPlayers.contains(playerName)) {
                            player.sendMessage(Text.of("§c[AI Misaki] 你不在AI聊天模式中"));
                            return 0;
                        }
                        
                        aiChatPlayers.remove(playerName);
                        player.sendMessage(Text.of("§b[AI Misaki] §e👋 已退出AI聊天模式，期待下次交流！"));
                        
                        return 1;
                    }))
                .then(CommandManager.literal("help")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        sendHelpMessage(player);
                        return 1;
                    }))
                .then(CommandManager.literal("status")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        String playerName = player.getName().getString();
                        boolean inAiChat = aiChatPlayers.contains(playerName);
                        
                        player.sendMessage(Text.of("§b[AI Misaki] §f状态：" + 
                            (inAiChat ? "§a在AI聊天模式中" : "§c不在AI聊天模式中")));
                        
                        return 1;
                    })));
        });
    }
    
    private static void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerName = sender.getName().getString();
            String messageContent = message.getContent().getString();
            
            // 检查是否在AI聊天模式
            if (aiChatPlayers.contains(playerName)) {
                // 在AI聊天模式中，所有消息都发送给AI处理
                handleAiChatMessage(sender, messageContent);
            }
        });
    }
    
    private static void handleAiChatMessage(ServerPlayerEntity player, String message) {
        // 异步处理AI响应，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                String response = workflowManager.processPlayerMessage(player, message);
                if (response != null && !response.isEmpty()) {
                    // 在主线程发送响应
                    server.execute(() -> {
                        player.sendMessage(Text.of("§b[AI Misaki] §f" + response));
                    });
                }
            } catch (Exception e) {
                AiMisakiMod.LOGGER.error("处理AI聊天消息时出错: " + e.getMessage(), e);
                server.execute(() -> {
                    player.sendMessage(Text.of("§c[AI Misaki] 抱歉，我遇到了一些问题 😅 请稍后再试"));
                });
            }
        });
    }
    
    private static void sendAiWelcomeMessage(ServerPlayerEntity player) {
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = player.getName().getString();
                String welcomePrompt = String.format(
                    "玩家 %s 刚刚进入AI聊天模式。请生成一个友好的欢迎消息，介绍你的主要功能。" +
                    "包括：物品管理、位置传送、建筑建议、天气控制、玩家管理、环境分析等功能。" +
                    "要个性化、简洁且鼓励玩家互动。用中文回复，不超过200字。",
                    playerName
                );
                
                String welcome = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();
                
                server.execute(() -> {
                    player.sendMessage(Text.of("§b[AI Misaki] §f" + welcome));
                });
                
            } catch (Exception e) {
                AiMisakiMod.LOGGER.error("生成AI欢迎消息时出错", e);
                server.execute(() -> {
                    player.sendMessage(Text.of("§b[AI Misaki] §f🤖 你好！我是AI助手Misaki，可以帮助你管理物品、传送位置、建筑建议等。告诉我你需要什么帮助吧！"));
                });
            }
        });
    }
    
    private static void sendHelpMessage(ServerPlayerEntity player) {
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§b=== AI Misaki 助手命令 ==="));
        player.sendMessage(Text.of("§f/ai chat  §7- 进入AI聊天模式"));
        player.sendMessage(Text.of("§f/ai exit  §7- 退出AI聊天模式"));
        player.sendMessage(Text.of("§f/ai help  §7- 显示此帮助信息"));
        player.sendMessage(Text.of("§f/ai status §7- 查看当前状态"));
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§b=== AI聊天模式功能 ==="));
        player.sendMessage(Text.of("§a🎒 物品管理 §7- \"我想要钻石剑\""));
        player.sendMessage(Text.of("§a🚀 智能传送 §7- \"带我回家\" / \"记住这里是我的农场\""));
        player.sendMessage(Text.of("§a🏗️ 建筑助手 §7- \"帮我设计一个城堡\""));
        player.sendMessage(Text.of("§a🌤️ 天气控制 §7- \"我想要晴天\""));
        player.sendMessage(Text.of("§a❤️ 玩家管理 §7- \"治疗我\" / \"查看玩家信息\""));
        player.sendMessage(Text.of("§a🔍 环境分析 §7- \"分析周围环境\" / \"寻找钻石\""));
        player.sendMessage(Text.of("§a🧠 智能记忆 §7- AI会记住你的偏好和重要位置"));
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§e💡 提示：在AI聊天模式中，直接说出你的需求，AI会自动理解并提供帮助！"));
    }
    
    /**
     * 检查玩家是否在AI聊天模式
     */
    public static boolean isInAiChatMode(String playerName) {
        return aiChatPlayers.contains(playerName);
    }
    
    /**
     * 获取AI聊天模式中的玩家数量
     */
    public static int getAiChatPlayerCount() {
        return aiChatPlayers.size();
    }
}