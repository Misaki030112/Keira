package com.hinadt.chat;

import com.hinadt.AiAusuka.AiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.ai.AiWorkflowManager;
import com.hinadt.tools.AdminTools;
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
        
        AiAusuka.AiMod.LOGGER.info("AI聊天系统初始化完成！");
    }
    
    /**
     * 检查用户名是否为AI助手名称，防止冒充
     */
    private static boolean isAiAssistantName(String playerName) {
        String normalizedName = playerName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String[] forbiddenNames = {
            "ausukaai", "ausuka", "aiausuka", "misaki", "aimisaki"
        };
        
        for (String forbidden : forbiddenNames) {
            if (normalizedName.contains(forbidden)) {
                return true;
            }
        }
        return false;
    }
    
    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("chat")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        String playerName = player.getName().getString();
                        // 检查用户名是否为AI助手名称
                        if (isAiAssistantName(playerName)) {
                            player.sendMessage(Text.of("§c[系统] 检测到AI助手身份，禁止进入AI聊天模式"));
                            return 0;
                        }
                        
                        if (aiChatPlayers.contains(playerName)) {
                            player.sendMessage(Text.of("§c[Ausuka.Ai] 你已经在AI聊天模式中了！使用 /ai exit 退出"));
                            return 0;
                        }
                        
                        aiChatPlayers.add(playerName);
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §a✨ 欢迎进入AI聊天模式！"));
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f现在你可以直接和我对话，我会理解你的需求并提供帮助"));
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f使用 /ai exit 退出AI聊天模式"));
                        
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
                            player.sendMessage(Text.of("§c[Ausuka.Ai] 你不在AI聊天模式中"));
                            return 0;
                        }
                        
                        aiChatPlayers.remove(playerName);
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §e👋 已退出AI聊天模式，期待下次交流！"));
                        
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
                        
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f状态：" + 
                            (inAiChat ? "§a在AI聊天模式中" : "§c不在AI聊天模式中")));
                        
                        return 1;
                    }))
                .then(CommandManager.literal("admin")
                    .then(CommandManager.literal("auto-msg")
                        .then(CommandManager.literal("toggle")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) return 0;
                                
                                if (!AdminTools.isPlayerAdmin(server, player)) {
                                    player.sendMessage(Text.of("§c[Ausuka.Ai] 只有管理员才能控制自动消息系统"));
                                    return 0;
                                }
                                
                                boolean newState = !IntelligentAutoMessageSystem.isSystemEnabled();
                                String result = IntelligentAutoMessageSystem.toggleAutoMessages(newState);
                                player.sendMessage(Text.of("§b[Ausuka.Ai] " + result));
                                
                                return 1;
                            }))
                        .then(CommandManager.literal("status")
                            .executes(context -> {
                                ServerPlayerEntity player = context.getSource().getPlayer();
                                if (player == null) return 0;
                                
                                boolean enabled = IntelligentAutoMessageSystem.isSystemEnabled();
                                int playerCount = server.getPlayerManager().getPlayerList().size();
                                
                                player.sendMessage(Text.of("§b[Ausuka.Ai] 自动消息系统状态: " + 
                                    (enabled ? "§a启用" : "§c禁用")));
                                player.sendMessage(Text.of("§b[Ausuka.Ai] 当前在线玩家: " + playerCount));
                                
                                return 1;
                            }))))
                .then(CommandManager.literal("new")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        String playerName = player.getName().getString();
                        String newSessionId = AiRuntime.getConversationMemory().startNewConversation(playerName);
                        
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §a✨ 已开始新的对话会话！"));
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f会话ID: " + newSessionId));
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f现在我们可以进行全新的对话，我会记住这次对话的上下文。"));
                        
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
                        player.sendMessage(Text.of("§b[Ausuka.Ai] §f" + response));
                    });
                }
            } catch (Exception e) {
                AiAusuka.AiMod.LOGGER.error("处理AI聊天消息时出错: " + e.getMessage(), e);
                server.execute(() -> {
                    player.sendMessage(Text.of("§c[Ausuka.Ai] 抱歉，我遇到了一些问题 😅 请稍后再试"));
                });
            }
        });
    }
    
    private static void sendAiWelcomeMessage(ServerPlayerEntity player) {
        CompletableFuture.runAsync(() -> {
            try {
                String playerName = player.getName().getString();
                boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
                
                // 构建详细的工具能力描述
                String toolCapabilities = """
                ## 🎮 我的核心能力 ##
                
                🎒 **智能物品管理**
                • 物品搜索与智能推荐：支持模糊搜索，"我想要建房子的材料"
                • 精确物品给予：支持数量控制和特殊属性设置
                • 背包分析：帮你整理和优化背包空间
                
                🚀 **智能传送系统**
                • 记忆位置传送：你可以说"记住这里是我的家"，之后"带我回家"
                • 坐标精确传送：支持三维坐标和多世界传送
                • 智能位置解析：理解"带我去地下"、"送我到天空"等自然语言
                
                🧠 **AI记忆系统**
                • 位置记忆：保存你的重要地点和建筑
                • 偏好学习：记住你的建筑风格、材料偏好、游戏习惯
                • 个性化服务：基于你的历史互动提供定制建议
                
                🌤️ **环境控制** (需要合适权限)
                • 天气管理：晴天、雨天、雷暴随你心意
                • 时间控制：白天黑夜，想要什么时候就什么时候
                • 世界信息：详细的环境和状态分析
                
                ❤️ **玩家服务**
                • 健康管理：治疗、恢复状态、清除负面效果
                • 社交助手：私信发送、玩家信息查询
                • 统计分析：成就追踪、游戏数据分析
                
                🔍 **智能分析**
                • 环境扫描：分析周围生物群系、资源分布、安全状况
                • 资源寻找：帮你定位特定矿物、建筑材料
                • 建筑建议：基于环境和偏好的个性化建筑指导
                """;
                
                String adminInfo = isAdmin ? AdminTools.getAdminWelcomeInfo(playerName) : "";
                
                String welcomePrompt = String.format("""
                你需要为刚进入AI聊天模式的玩家 %s 生成一条个性化欢迎消息。
                
                玩家权限状态：%s
                
                以下是你的详细能力描述：
                %s
                
                %s
                
                请生成一条欢迎消息，要求：
                1. 热情友好，体现AI伴侣的特色
                2. 简要介绍核心功能，让玩家了解你能做什么
                3. 鼓励玩家尝试自然语言交流
                4. 个性化称呼玩家
                5. %s
                6. 长度控制在150-200字
                7. 使用中文，语调要亲切自然
                8. 可以包含适当的emoji增加亲切感
                
                记住：你是玩家信赖的AI伙伴Ausuka.Ai，智能、贴心、专业！
                """, 
                playerName,
                isAdmin ? "管理员用户，拥有完整权限" : "普通用户，部分功能需要权限验证",
                toolCapabilities,
                adminInfo,
                isAdmin ? "强调管理员专属功能" : "说明部分功能需要管理员权限"
                );
                
                String welcome = AiRuntime.AIClient
                    .prompt()
                    .user(welcomePrompt)
                    .call()
                    .content();
                
                server.execute(() -> {
                    player.sendMessage(Text.of("§b[Ausuka.Ai] §f" + welcome));
                });
                
            } catch (Exception e) {
                AiAusuka.AiMod.LOGGER.error("生成AI欢迎消息时出错", e);
                server.execute(() -> {
                    String fallbackWelcome = "🤖 你好 " + player.getName().getString() + "！我是AI助手Ausuka.Ai，" +
                        "可以帮助你管理物品、智能传送、记忆重要位置、建筑指导等。" +
                        "直接告诉我你需要什么帮助，我会智能理解并为你服务！✨";
                    player.sendMessage(Text.of("§b[Ausuka.Ai] §f" + fallbackWelcome));
                });
            }
        });
    }
    
    private static void sendHelpMessage(ServerPlayerEntity player) {
        boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
        
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§b=== Ausuka.Ai 助手命令 ==="));
        player.sendMessage(Text.of("§f/ai chat   §7- 进入AI聊天模式"));
        player.sendMessage(Text.of("§f/ai exit   §7- 退出AI聊天模式"));
        player.sendMessage(Text.of("§f/ai new    §7- 开始新的对话会话（清除对话记忆）"));
        player.sendMessage(Text.of("§f/ai help   §7- 显示此帮助信息"));
        player.sendMessage(Text.of("§f/ai status §7- 查看当前状态"));
        
        if (isAdmin) {
            player.sendMessage(Text.of("§c=== 管理员专用命令 ==="));
            player.sendMessage(Text.of("§f/ai admin auto-msg toggle §7- 切换自动消息系统"));
            player.sendMessage(Text.of("§f/ai admin auto-msg status §7- 查看自动消息系统状态"));
        }
        
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§b=== AI聊天模式功能 ==="));
        player.sendMessage(Text.of("§a🎒 智能物品管理 §7- \"我想要钻石剑\" / \"帮我整理背包\""));
        player.sendMessage(Text.of("§a🚀 智能传送系统 §7- \"带我回家\" / \"记住这里是我的农场\""));
        player.sendMessage(Text.of("§a🧠 AI记忆系统 §7- \"记住我喜欢现代建筑风格\""));
        player.sendMessage(Text.of("§a🏗️ 建筑助手 §7- \"帮我设计一个城堡\""));
        player.sendMessage(Text.of("§a🌤️ 环境控制 §7- \"我想要晴天\" / \"设置为白天\""));
        player.sendMessage(Text.of("§a❤️ 玩家服务 §7- \"治疗我\" / \"查看玩家信息\""));
        player.sendMessage(Text.of("§a🔍 环境分析 §7- \"分析周围环境\" / \"寻找钻石\""));
        player.sendMessage(Text.of("§a💬 对话记忆 §7- AI会记住整个对话过程和上下文"));
        
        if (isAdmin) {
            player.sendMessage(Text.of("§c🛡️ 管理员功能 §7- 服务器管理、权限控制、系统设置"));
        }
        
        player.sendMessage(Text.of(""));
        player.sendMessage(Text.of("§e💡 提示：在AI聊天模式中，直接说出你的需求，AI会自动理解并提供帮助！"));
        player.sendMessage(Text.of("§e🔄 使用 /ai new 开始新对话可以清除之前的对话记忆"));
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