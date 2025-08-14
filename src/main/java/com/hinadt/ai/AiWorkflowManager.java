package com.hinadt.ai;

import com.hinadt.AiMisakiMod;
import com.hinadt.tools.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Workflow Manager - AI工作流管理器
 * 使用AI驱动的方式处理玩家消息，自动选择合适的工具和工作流
 */
public class AiWorkflowManager {
    
    private final MinecraftServer server;
    private final McTools mcTools;
    private final TeleportationTools teleportTools;
    private final WeatherTools weatherTools;
    private final PlayerStatsTools playerStatsTools;
    private final WorldAnalysisTools worldAnalysisTools;
    private final MemorySystem memorySystem;
    
    public AiWorkflowManager(MinecraftServer server) {
        this.server = server;
        this.mcTools = new McTools(server);
        this.teleportTools = new TeleportationTools(server);
        this.weatherTools = new WeatherTools(server);
        this.playerStatsTools = new PlayerStatsTools(server);
        this.worldAnalysisTools = new WorldAnalysisTools(server);
        this.memorySystem = new MemorySystem();
    }
    
    /**
     * 处理玩家消息的主入口
     */
    public String processPlayerMessage(ServerPlayerEntity player, String message) {
        try {
            // 获取玩家当前状态信息
            String playerContext = buildPlayerContext(player);
            
            // 第一阶段：AI分析消息并决定工作流
            String workflowDecision = analyzeMessageAndDecideWorkflow(player, message, playerContext);
            
            // 第二阶段：根据工作流决策执行具体任务
            return executeWorkflow(player, message, workflowDecision, playerContext);
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理玩家消息时出错", e);
            return "抱歉，我在处理你的请求时遇到了问题。请稍后再试。";
        }
    }
    
    private String analyzeMessageAndDecideWorkflow(ServerPlayerEntity player, String message, String playerContext) {
        String analysisPrompt = String.format("""
            作为AI助手，我需要分析玩家的请求并决定使用哪些工具。
            
            玩家：%s
            消息：%s
            玩家状态：%s
            
            请分析这条消息并决定需要使用的功能类别：
            1. item_management - 物品给予、物品搜索相关
            2. teleportation - 传送、位置相关  
            3. memory_operations - 保存/回忆位置、偏好设置
            4. building_assistance - 建筑建议、设计帮助
            5. weather_control - 天气、时间控制
            6. player_management - 治疗、玩家信息、私信
            7. world_analysis - 环境分析、资源寻找
            8. general_chat - 普通聊天、游戏建议
            
            只返回类别名称，如果需要多个功能，用逗号分隔。例如：item_management 或 teleportation,memory_operations
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(analysisPrompt)
            .call()
            .content()
            .trim();
    }
    
    private String executeWorkflow(ServerPlayerEntity player, String message, String workflow, String playerContext) {
        String[] workflows = workflow.split(",");
        StringBuilder results = new StringBuilder();
        
        for (String workflowType : workflows) {
            String result = executeSpecificWorkflow(player, message, workflowType.trim(), playerContext);
            if (result != null && !result.isEmpty()) {
                if (results.length() > 0) {
                    results.append("\n\n");
                }
                results.append(result);
            }
        }
        
        return results.toString();
    }
    
    private String executeSpecificWorkflow(ServerPlayerEntity player, String message, String workflowType, String playerContext) {
        switch (workflowType.toLowerCase()) {
            case "item_management":
                return handleItemManagement(player, message, playerContext);
                
            case "teleportation":
                return handleTeleportation(player, message, playerContext);
                
            case "memory_operations":
                return handleMemoryOperations(player, message, playerContext);
                
            case "building_assistance":
                return handleBuildingAssistance(player, message, playerContext);
                
            case "weather_control":
                return handleWeatherControl(player, message, playerContext);
                
            case "player_management":
                return handlePlayerManagement(player, message, playerContext);
                
            case "world_analysis":
                return handleWorldAnalysis(player, message, playerContext);
                
            case "general_chat":
                return handleGeneralChat(player, message, playerContext);
                
            default:
                return handleGeneralChat(player, message, playerContext);
        }
    }
    
    private String handleItemManagement(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家请求物品相关操作：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            请使用 list_items 搜索相关物品，然后使用 give_item 给予最合适的物品。
            如果找不到确切匹配，选择最相似的物品。用中文回复操作结果。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(mcTools)
            .call()
            .content();
    }
    
    private String handleTeleportation(ServerPlayerEntity player, String message, String playerContext) {
        // 首先检查是否需要查询记忆中的位置
        String memoryCheck = checkForSavedLocations(player, message);
        
        String prompt = String.format("""
            玩家请求传送操作：
            玩家：%s
            消息：%s
            玩家状态：%s
            记忆位置信息：%s
            
            请使用 teleport_player 工具传送玩家。如果是具体坐标，直接传送。
            如果是地名或"家"、"农场"等位置，优先使用记忆中的位置，其次使用预设位置。
            用中文回复传送结果。
            """, player.getName().getString(), message, playerContext, memoryCheck);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(teleportTools)
            .call()
            .content();
    }
    
    private String handleMemoryOperations(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家想要记忆相关操作：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            如果玩家说"这里是我的X"、"记住这个地方"、"保存位置"等，使用 save_location 保存当前位置。
            如果玩家想查看保存的位置，使用 list_saved_locations。
            如果玩家想设置偏好，使用 save_player_preference。
            根据消息内容智能判断并执行相应操作。用中文回复。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(memorySystem)
            .call()
            .content();
    }
    
    private String handleBuildingAssistance(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家需要建筑帮助：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            请提供详细的建筑建议，包括：
            1. 需要的材料清单
            2. 建造步骤指南
            3. 设计思路和技巧
            4. 实用建议
            
            回复要实用、详细且容易理解，用中文回复。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .call()
            .content();
    }
    
    private String handleWeatherControl(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家请求天气/时间控制：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            请使用 change_weather 或 set_time 工具改变天气或时间。
            支持的天气：晴天/clear, 雨天/rain, 雷雨/thunder
            支持的时间：白天/day, 夜晚/night, 正午/noon, 午夜/midnight
            用中文回复操作结果。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(weatherTools)
            .call()
            .content();
    }
    
    private String handlePlayerManagement(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家请求玩家管理操作：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            可用操作：
            - heal_player: 治疗玩家
            - get_player_info: 获取玩家信息
            - list_online_players: 列出在线玩家
            - send_message_to_player: 发送私信
            
            根据玩家请求使用合适的工具，用中文回复结果。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(playerStatsTools)
            .call()
            .content();
    }
    
    private String handleWorldAnalysis(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            玩家请求世界分析：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            可用操作：
            - analyze_surroundings: 分析周围环境
            - find_resources: 寻找特定资源
            
            根据玩家请求使用合适的工具分析环境或寻找资源，用中文回复结果。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .tools(worldAnalysisTools)
            .call()
            .content();
    }
    
    private String handleGeneralChat(ServerPlayerEntity player, String message, String playerContext) {
        String prompt = String.format("""
            和玩家进行普通聊天：
            玩家：%s
            消息：%s
            玩家状态：%s
            
            请作为友善的AI助手回复玩家，可以：
            1. 提供游戏建议和技巧
            2. 回答游戏相关问题
            3. 进行友好的聊天互动
            4. 推荐合适的活动
            5. 如果玩家需要具体帮助，建议他们使用具体功能
            
            回复要简洁友好，用中文回复，不要超过150字。
            """, player.getName().getString(), message, playerContext);
        
        return AiRuntime.AIClient
            .prompt()
            .user(prompt)
            .call()
            .content();
    }
    
    private String buildPlayerContext(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        String worldName = player.getServerWorld().getRegistryKey().getValue().toString();
        int health = (int) player.getHealth();
        int hunger = player.getHungerManager().getFoodLevel();
        String gameMode = player.interactionManager.getGameMode().getName();
        int experienceLevel = player.experienceLevel;
        
        return String.format("""
            位置: (%d, %d, %d) 在 %s
            生命值: %d/20
            饥饿值: %d/20
            游戏模式: %s
            经验等级: %d
            """, pos.getX(), pos.getY(), pos.getZ(), worldName, 
            health, hunger, gameMode, experienceLevel);
    }
    
    private String checkForSavedLocations(ServerPlayerEntity player, String message) {
        try {
            String prompt = String.format("""
                检查玩家消息中是否包含位置名称，如果有，使用 list_saved_locations 查看玩家保存的位置。
                玩家：%s
                消息：%s
                
                如果消息中包含位置相关词汇，返回位置列表，否则返回"无需查询位置"。
                """, player.getName().getString(), message);
            
            return AiRuntime.AIClient
                .prompt()
                .user(prompt)
                .tools(memorySystem)
                .call()
                .content();
                
        } catch (Exception e) {
            return "无需查询位置";
        }
    }
}