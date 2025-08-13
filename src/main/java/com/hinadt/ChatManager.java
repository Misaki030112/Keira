package com.hinadt;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI聊天管理器 - 处理玩家消息并提供智能回复
 * 支持的功能：
 * 1. 给我 XXX - 物品给予功能
 * 2. 我要去 XXX - 传送功能
 * 3. 帮我建造 XXX - 建筑辅助
 * 4. 天气控制 - 改变天气和时间
 * 5. 普通聊天 - AI智能回复
 */
public class ChatManager {
    private static MinecraftServer server;
    private static McTools mcTools;
    private static TeleportationTools teleportTools;
    private static WeatherTools weatherTools;
    private static PlayerStatsTools playerStatsTools;
    private static WorldAnalysisTools worldAnalysisTools;
    
    // 正则表达式匹配模式
    private static final Pattern GIVE_PATTERN = Pattern.compile("(?:给我|give me)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TELEPORT_PATTERN = Pattern.compile("(?:我要去|带我去|tp to|go to)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUILD_PATTERN = Pattern.compile("(?:帮我建造|建造|build me|build)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEATHER_PATTERN = Pattern.compile("(?:天气|weather)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:时间|time)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEAL_PATTERN = Pattern.compile("(?:治疗|heal)(?:\\s+(.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INFO_PATTERN = Pattern.compile("(?:玩家信息|player info|查看)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANALYZE_PATTERN = Pattern.compile("(?:分析环境|analyze|环境|surroundings)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIND_PATTERN = Pattern.compile("(?:寻找|找|find)\\s+(.+)", Pattern.CASE_INSENSITIVE);
    
    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        mcTools = new McTools(server);
        teleportTools = new TeleportationTools(server);
        weatherTools = new WeatherTools(server);
        playerStatsTools = new PlayerStatsTools(server);
        worldAnalysisTools = new WorldAnalysisTools(server);
        
        // 注册聊天事件监听器
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            handleChatMessage(sender, message.getContent().getString());
        });
        
        // 发送欢迎消息
        server.execute(() -> {
            server.getPlayerManager().broadcast(
                Text.of("§b[AI Misaki] §f🤖 AI助手已上线！输入 '帮助' 查看可用功能"), 
                false
            );
        });
        
        AiMisakiMod.LOGGER.info("聊天管理器初始化完成！");
    }
    
    private static void handleChatMessage(ServerPlayerEntity player, String message) {
        // 异步处理AI响应，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                String response = processMessage(player, message);
                if (response != null && !response.isEmpty()) {
                    // 在主线程发送消息
                    server.execute(() -> {
                        server.getPlayerManager().broadcast(
                            Text.of("§b[AI Misaki] §f" + response), 
                            false
                        );
                    });
                }
            } catch (Exception e) {
                AiMisakiMod.LOGGER.error("处理聊天消息时出错: " + e.getMessage(), e);
                server.execute(() -> {
                    player.sendMessage(Text.of("§c[AI Misaki] 抱歉，我遇到了一些问题 😅"));
                });
            }
        });
    }
    
    private static String processMessage(ServerPlayerEntity player, String message) {
        String playerName = player.getName().getString();
        
        // 检查帮助命令
        if (message.toLowerCase().matches("(?:帮助|help|功能|commands?)")) {
            return getHelpMessage();
        }
        
        // 检查在线玩家列表
        if (message.toLowerCase().matches("(?:在线玩家|online players|玩家列表|who)")) {
            return handleOnlinePlayersRequest();
        }
        
        // 检查是否是治疗请求
        Matcher healMatcher = HEAL_PATTERN.matcher(message);
        if (healMatcher.find()) {
            String targetPlayer = healMatcher.group(1);
            if (targetPlayer == null || targetPlayer.trim().isEmpty()) {
                targetPlayer = playerName; // 治疗自己
            }
            return handleHealRequest(player, targetPlayer.trim());
        }
        
        // 检查是否是玩家信息请求
        Matcher playerInfoMatcher = PLAYER_INFO_PATTERN.matcher(message);
        if (playerInfoMatcher.find()) {
            return handlePlayerInfoRequest(player, playerInfoMatcher.group(1).trim());
        }
        
        // 检查是否是环境分析请求
        if (ANALYZE_PATTERN.matcher(message).find()) {
            return handleAnalyzeRequest(player);
        }
        
        // 检查是否是资源寻找请求
        Matcher findMatcher = FIND_PATTERN.matcher(message);
        if (findMatcher.find()) {
            return handleFindRequest(player, findMatcher.group(1).trim());
        }
        
        // 检查是否是物品给予请求
        Matcher giveMatcher = GIVE_PATTERN.matcher(message);
        if (giveMatcher.find()) {
            return handleGiveRequest(player, giveMatcher.group(1).trim());
        }
        
        // 检查是否是传送请求
        Matcher teleportMatcher = TELEPORT_PATTERN.matcher(message);
        if (teleportMatcher.find()) {
            return handleTeleportRequest(player, teleportMatcher.group(1).trim());
        }
        
        // 检查是否是建造请求
        Matcher buildMatcher = BUILD_PATTERN.matcher(message);
        if (buildMatcher.find()) {
            return handleBuildRequest(player, buildMatcher.group(1).trim());
        }
        
        // 检查是否是天气控制请求
        Matcher weatherMatcher = WEATHER_PATTERN.matcher(message);
        if (weatherMatcher.find()) {
            return handleWeatherRequest(player, weatherMatcher.group(1).trim());
        }
        
        // 检查是否是时间控制请求
        Matcher timeMatcher = TIME_PATTERN.matcher(message);
        if (timeMatcher.find()) {
            return handleTimeRequest(player, timeMatcher.group(1).trim());
        }
        
        // 普通聊天 - AI智能回复
        return handleGeneralChat(player, message);
    }
    
    private static String getHelpMessage() {
        return """
        🤖 AI Misaki助手功能列表：
        
        📦 物品功能：
        • "给我 [物品名]" - 获得指定物品
        • 例：给我钻石剑、给我面包
        
        🚀 传送功能：
        • "我要去 [地点]" - 传送到指定位置
        • 支持：出生点、主城、矿洞、农场、海边、山顶
        • 坐标：我要去 100 70 200
        • 玩家：我要去 [玩家名]
        
        🏗️ 建造功能：
        • "帮我建造 [建筑]" - 获得建造建议
        • 例：帮我建造城堡、建造农场
        
        🌤️ 天气控制：
        • "天气 [类型]" - 改变天气
        • 支持：晴天、雨天、雷雨
        
        🕐 时间控制：
        • "时间 [类型]" - 改变时间
        • 支持：白天、夜晚、正午、午夜
        
        ❤️ 玩家管理：
        • "治疗" 或 "治疗 [玩家名]" - 治疗玩家
        • "玩家信息 [玩家名]" - 查看玩家状态
        • "在线玩家" - 查看在线玩家列表
        
        🔍 环境分析：
        • "分析环境" - 分析周围环境
        • "寻找 [资源]" - 寻找特定资源
        • 支持寻找：矿物、木材、水源等
        
        💬 智能聊天：
        • 直接聊天获得AI回复和游戏建议
        """;
    }
    
    private static String handleGiveRequest(ServerPlayerEntity player, String itemName) {
        try {
            String prompt = String.format(
                "玩家 %s 想要物品: '%s'。请使用 list_items 工具搜索相关物品，然后使用 give_item 工具给予最合适的物品。" +
                "如果找不到完全匹配的物品，请选择最相似的物品。请用中文回复操作结果。",
                player.getName().getString(), itemName
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(mcTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理物品给予请求时出错", e);
            return "抱歉，我无法处理这个物品请求：" + e.getMessage();
        }
    }
    
    private static String handleTeleportRequest(ServerPlayerEntity player, String location) {
        try {
            String prompt = String.format(
                "玩家 %s 想要传送到: '%s'。请使用 teleport_player 工具将玩家传送到合适的位置。" +
                "如果是具体坐标，请直接传送。如果是地名或建筑名称，请选择合适的坐标。请用中文回复操作结果。",
                player.getName().getString(), location
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(teleportTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理传送请求时出错", e);
            return "抱歉，我无法处理这个传送请求：" + e.getMessage();
        }
    }
    
    private static String handleBuildRequest(ServerPlayerEntity player, String buildDescription) {
        try {
            BlockPos playerPos = player.getBlockPos();
            String prompt = String.format(
                "玩家 %s 在位置 (%d, %d, %d) 想要建造: '%s'。" +
                "请提供详细的建造建议，包括：1)需要的材料清单 2)建造步骤 3)设计思路 4)实用技巧。" +
                "回复要实用且容易理解，用中文回复。",
                player.getName().getString(), 
                playerPos.getX(), playerPos.getY(), playerPos.getZ(),
                buildDescription
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理建造请求时出错", e);
            return "抱歉，我无法处理这个建造请求：" + e.getMessage();
        }
    }
    
    private static String handleWeatherRequest(ServerPlayerEntity player, String weatherType) {
        try {
            String prompt = String.format(
                "玩家 %s 想要改变天气为: '%s'。请使用 change_weather 工具改变天气。" +
                "支持的天气类型：晴天/clear, 雨天/rain, 雷雨/thunder。请用中文回复操作结果。",
                player.getName().getString(), weatherType
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(weatherTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理天气请求时出错", e);
            return "抱歉，我无法处理这个天气请求：" + e.getMessage();
        }
    }
    
    private static String handleTimeRequest(ServerPlayerEntity player, String timeType) {
        try {
            String prompt = String.format(
                "玩家 %s 想要改变时间为: '%s'。请使用 set_time 工具改变时间。" +
                "支持的时间类型：白天/day, 夜晚/night, 正午/noon, 午夜/midnight。请用中文回复操作结果。",
                player.getName().getString(), timeType
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(weatherTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理时间请求时出错", e);
            return "抱歉，我无法处理这个时间请求：" + e.getMessage();
        }
    }
    
    private static String handleGeneralChat(ServerPlayerEntity player, String message) {
        try {
            // 获取玩家当前状态信息
            BlockPos pos = player.getBlockPos();
            String worldName = player.getWorld().getRegistryKey().getValue().toString();
            int health = (int) player.getHealth();
            int hunger = player.getHungerManager().getFoodLevel();
            
            String prompt = String.format(
                "玩家 %s 在 %s 世界的坐标 (%d, %d, %d) 说: '%s'。" +
                "玩家当前生命值: %d/20，饥饿值: %d/20。" +
                "请作为一个友善的AI助手回复玩家，可以：" +
                "1) 提供游戏建议和技巧 2) 回答游戏相关问题 3) 进行友好的聊天互动 4) 推荐合适的活动。" +
                "回复要简洁友好，用中文回复，不要超过150字。如果玩家需要具体帮助，引导他们使用相应的功能命令。",
                player.getName().getString(), worldName,
                pos.getX(), pos.getY(), pos.getZ(),
                message, health, hunger
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理普通聊天时出错", e);
            return "你好！我是AI助手Misaki，很高兴和你聊天 😊 输入 '帮助' 查看我的功能！";
        }
    }
    
    private static String handleOnlinePlayersRequest() {
        try {
            return playerStatsTools.listOnlinePlayers();
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("获取在线玩家列表时出错", e);
            return "抱歉，无法获取在线玩家列表：" + e.getMessage();
        }
    }
    
    private static String handleHealRequest(ServerPlayerEntity requester, String targetPlayerName) {
        try {
            String prompt = String.format(
                "玩家 %s 想要治疗玩家 '%s'。请使用 heal_player 工具治疗指定玩家。" +
                "治疗会恢复目标玩家的生命值和饥饿值到满值。请用中文回复操作结果。",
                requester.getName().getString(), targetPlayerName
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(playerStatsTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理治疗请求时出错", e);
            return "抱歉，我无法处理这个治疗请求：" + e.getMessage();
        }
    }
    
    private static String handlePlayerInfoRequest(ServerPlayerEntity requester, String targetPlayerName) {
        try {
            String prompt = String.format(
                "玩家 %s 想要查看玩家 '%s' 的信息。请使用 get_player_info 工具获取目标玩家的详细信息。" +
                "请用中文回复查询结果。",
                requester.getName().getString(), targetPlayerName
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(playerStatsTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理玩家信息请求时出错", e);
            return "抱歉，我无法获取玩家信息：" + e.getMessage();
        }
    }
    
    private static String handleAnalyzeRequest(ServerPlayerEntity player) {
        try {
            String prompt = String.format(
                "玩家 %s 想要分析周围的环境。请使用 analyze_surroundings 工具分析玩家周围的环境信息，" +
                "包括生物群系、方块、生物等。请用中文回复分析结果。",
                player.getName().getString()
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(worldAnalysisTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理环境分析请求时出错", e);
            return "抱歉，我无法分析环境：" + e.getMessage();
        }
    }
    
    private static String handleFindRequest(ServerPlayerEntity player, String resource) {
        try {
            String prompt = String.format(
                "玩家 %s 想要寻找 '%s'。请使用 find_resources 工具帮助玩家找到相关资源。" +
                "请根据资源名称选择合适的资源类型（如：ore矿物、wood木材、water水源等）。请用中文回复搜索结果。",
                player.getName().getString(), resource
            );
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(worldAnalysisTools)
                .call()
                .content();
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理资源寻找请求时出错", e);
            return "抱歉，我无法寻找资源：" + e.getMessage();
        }
    }
}