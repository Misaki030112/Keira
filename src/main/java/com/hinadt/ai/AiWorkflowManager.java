package com.hinadt.ai;

import com.hinadt.AiMisakiMod;
import com.hinadt.tools.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Workflow Manager - AI工作流管理器
 * 使用单次AI调用，让AI自主选择合适的工具来完成玩家请求
 * 
 * 核心原则：
 * 1. 一次AI调用解决问题，而非多次调用
 * 2. 提供详细的上下文信息和工具描述
 * 3. 让AI自主决策使用哪些工具以及调用顺序
 * 4. 智能权限验证和安全控制
 */
public class AiWorkflowManager {
    
    private final MinecraftServer server;
    private final McTools mcTools;
    private final TeleportationTools teleportTools;
    private final WeatherTools weatherTools;
    private final PlayerStatsTools playerStatsTools;
    private final WorldAnalysisTools worldAnalysisTools;
    private final ConversationMemorySystem memorySystem;
    private final MemoryTools memoryTools;
    private final AdminTools adminTools;
    
    public AiWorkflowManager(MinecraftServer server) {
        this.server = server;
        this.mcTools = new McTools(server);
        this.teleportTools = new TeleportationTools(server);
        this.weatherTools = new WeatherTools(server);
        this.playerStatsTools = new PlayerStatsTools(server);
        this.worldAnalysisTools = new WorldAnalysisTools(server);
        this.memorySystem = AiRuntime.getConversationMemory();
        this.memoryTools = new MemoryTools(server);
        
        // 初始化MOD管理员系统（如果还未初始化）
        AiRuntime.initModAdminSystem(server);
        this.adminTools = new AdminTools(server, AiRuntime.getModAdminSystem());
    }
    
    /**
     * 处理玩家消息的主入口 - 使用单次AI调用完成所有任务
     */
    public String processPlayerMessage(ServerPlayerEntity player, String message) {
        try {
            String playerName = player.getName().getString();
            
            // 保存用户消息到对话记忆
            AiRuntime.getConversationMemory().saveUserMessage(playerName, message);
            
            // 构建详细的上下文信息
            String detailedContext = buildDetailedPlayerContext(player);
            
            // 获取对话历史上下文
            String conversationContext = AiRuntime.getConversationMemory().getConversationContext(playerName);
            
            // 检查玩家权限状态
            boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
            String permissionContext = isAdmin ? 
                "玩家具有管理员权限，可以执行所有操作包括危险操作。" : 
                "玩家为普通用户，危险操作需要权限验证。";
            
            // 构建超详细的AI提示词
            String comprehensivePrompt = buildComprehensivePrompt(player, message, detailedContext, conversationContext, permissionContext, isAdmin);
            
            // 一次性AI调用，提供所有相关工具
            String aiResponse = AiRuntime.AIClient
                .prompt()
                .user(comprehensivePrompt)
                .tools(
                    // 提供所有工具给AI，让AI自主选择和调用
                    mcTools,               // 物品管理工具
                    teleportTools,         // 传送工具
                    memoryTools,           // 记忆系统工具
                    weatherTools,          // 天气控制工具
                    playerStatsTools,      // 玩家管理工具
                    worldAnalysisTools,    // 世界分析工具
                    adminTools             // 管理员权限工具
                )
                .call()
                .content();
            
            // 保存AI响应到对话记忆
            AiRuntime.getConversationMemory().saveAiResponse(playerName, aiResponse);
            
            return aiResponse;
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("处理玩家消息时出错: " + e.getMessage(), e);
            return "😅 抱歉，我在处理你的请求时遇到了一些技术问题。请稍后再试，或者尝试用不同的方式描述你的需求。";
        }
    }
    
    /**
     * 构建超详细的AI提示词，包含完整的游戏上下文和指导
     */
    private String buildComprehensivePrompt(ServerPlayerEntity player, String message, String detailedContext, String conversationContext, String permissionContext, boolean isAdmin) {
        String playerName = player.getName().getString();
        
        // 获取玩家的记忆和偏好
        String memoryContext = getPlayerMemoryContext(playerName);
        
        // 构建服务器整体状态
        String serverContext = buildServerContext();
        
        return String.format("""
            # AI Misaki 智能助手 - Minecraft服务器AI伴侣
            
            你是一个高度智能的Minecraft AI助手，名叫Ausuka.Ai。你的任务是理解玩家需求并使用合适的工具来帮助他们。
            
            %s
            
            ## 当前情况分析
            
            ### 玩家信息
            **玩家姓名**: %s
            **权限状态**: %s
            **详细状态**: 
            %s
            
            ### 玩家记忆与偏好
            %s
            
            ### 服务器状态
            %s
            
            ### 玩家请求
            **原始消息**: "%s"
            
            ## 你的能力与工具
            
            你拥有以下工具集合，可以根据玩家需求智能选择和组合使用：
            
            ### 🎒 物品管理工具 (McTools)
            - **list_items**: 搜索游戏中的物品，支持模糊匹配和智能推荐
            - **give_item**: 给予玩家指定物品，支持数量和NBT数据设置
            - **get_inventory_info**: 查看玩家背包内容和物品统计
            
            ### 🚀 智能传送工具 (TeleportationTools)  
            - **teleport_player**: 智能传送系统，支持记忆位置、坐标、预设地点、玩家位置、多世界传送
            
            ### 🧠 记忆系统工具 (ConversationMemorySystem)
            - **save_location**: 保存玩家定义的重要位置("这里是我的家"→保存位置)
            - **get_saved_location**: 获取特定位置信息用于传送或回忆
            - **list_saved_locations**: 列出玩家所有保存的位置
            - **save_player_preference**: 保存玩家偏好(建筑风格、材料偏好等)
            - **get_player_preference**: 获取玩家偏好以提供个性化建议
            - **save_global_memory**: 保存全服共享信息(规则、事件、公共建筑等)
            - **get_global_memory**: 获取全服共享记忆信息
            
            ### 🌤️ 天气控制工具 (WeatherTools)
            - **change_weather**: 改变天气状态(晴天、雨天、雷暴)
            - **set_time**: 设置游戏时间(白天、夜晚、正午、午夜、具体时间)
            - **get_world_info**: 获取世界详细信息(时间、天气、难度等)
            
            ### ❤️ 玩家管理工具 (PlayerStatsTools)
            - **get_player_info**: 获取玩家详细信息(位置、状态、背包等)
            - **heal_player**: 治疗玩家(恢复生命值、饥饿值、清除负面效果)
            - **list_online_players**: 列出所有在线玩家及其状态
            - **send_message_to_player**: 发送私信给指定玩家
            - **get_player_achievements**: 获取玩家成就和统计信息
            
            ### 🔍 世界分析工具 (WorldAnalysisTools)
            - **analyze_surroundings**: 分析玩家周围环境(生物群系、资源、危险等)
            - **find_resources**: 在指定范围内寻找特定资源或方块
            - **get_biome_info**: 获取当前生物群系的详细信息和建议
            
            ### 🛡️ 管理员权限工具 (AdminTools) %s
            - **check_admin_permission**: 检查玩家管理员权限状态
            - **require_admin_or_deny**: 执行危险操作前的权限验证
            - **get_admin_welcome_info**: 获取管理员专用功能说明
            
            ## 行为指导原则
            
            ### 🎯 核心原则
            1. **理解意图**: 深度分析玩家的真实需求，不仅仅是字面意思
            2. **智能组合**: 根据需要组合使用多个工具，创造性地解决问题
            3. **个性化服务**: 基于玩家记忆和偏好提供定制化建议
            4. **安全第一**: 危险操作必须进行权限验证
            5. **友好互动**: 始终保持友好、耐心、有趣的语调
            
            ### ⚡ 响应策略
            - **物品需求**: "我想要X" → 搜索物品 → 检查背包空间 → 给予合适数量 → 提供使用建议
            - **位置相关**: "带我回家" → 查询记忆位置 → 智能传送 → 到达确认
            - **记忆指令**: "记住这里是我的X" → 保存当前位置 → 确认保存 → 提供后续使用方法
            - **建筑帮助**: "建造X" → 分析环境 → 提供材料清单 → 给予必要物品 → 建筑指导
            - **状态查询**: "我在哪" → 获取位置信息 → 分析周围环境 → 提供导航建议
            - **管理请求**: "踢掉XX" → 验证管理员权限 → 执行或友好拒绝
            
            ### 🚨 权限控制
            %s
            
            ## 响应要求
            
            1. **调用顺序**: 先调用必要的查询工具获取信息，再调用执行工具完成任务
            2. **错误处理**: 遇到问题时给出清晰的解释和替代方案
            3. **完整性**: 确保任务完全完成，必要时进行确认
            4. **反馈**: 操作完成后提供清晰的结果反馈
            5. **建议**: 主动提供相关的游戏建议和优化提示
            
            请分析玩家的请求"%s"，选择合适的工具组合来满足需求，并提供友好专业的服务。记住你是玩家信赖的AI伴侣！
            """, 
            conversationContext.isEmpty() ? "" : conversationContext,
            playerName, 
            permissionContext, 
            detailedContext, 
            memoryContext, 
            serverContext, 
            message,
            isAdmin ? "\n            (当前玩家拥有管理员权限，可以使用所有管理功能)" : "\n            (当前玩家为普通用户，危险操作需要权限验证)",
            isAdmin ? 
                "当前玩家具有管理员权限，可以执行包括踢人、封禁、服务器控制等所有操作。" : 
                "当前玩家为普通用户。在执行踢人、封禁、天气控制、自动消息系统控制等危险操作前，必须使用require_admin_or_deny工具验证权限。如果权限不足，要友好地拒绝并解释原因。",
            message
        );
    }
    
    /**
     * 构建详细的玩家上下文信息
     */
    private String buildDetailedPlayerContext(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        ServerWorld world = player.getServerWorld();
        String worldName = getWorldDisplayName(world);
        
        // 基础状态信息
        StringBuilder context = new StringBuilder();
        context.append(String.format("**基础信息**:\n"));
        context.append(String.format("- 位置: (%d, %d, %d) 在 %s\n", pos.getX(), pos.getY(), pos.getZ(), worldName));
        context.append(String.format("- 生命值: %d/20\n", (int) player.getHealth()));
        context.append(String.format("- 饥饿值: %d/20\n", player.getHungerManager().getFoodLevel()));
        context.append(String.format("- 经验等级: %d\n", player.experienceLevel));
        context.append(String.format("- 游戏模式: %s\n", player.interactionManager.getGameMode().getName()));
        
        // 位置分析
        context.append("\n**位置分析**:\n");
        if (pos.getY() < -32) {
            context.append("- 处于地下深层，接近基岩层，适合寻找钻石等稀有矿物\n");
        } else if (pos.getY() < 32) {
            context.append("- 处于地下采矿层，铁矿、煤矿、金矿丰富\n");
        } else if (pos.getY() < 64) {
            context.append("- 处于地表附近，适合建造地下室或寻找洞穴\n");
        } else if (pos.getY() > 120) {
            context.append("- 处于高空，视野开阔，适合建造高塔或观景台\n");
        } else {
            context.append("- 处于正常地表高度，适合大部分生存活动\n");
        }
        
        // 生物群系信息
        try {
            var biomeEntry = world.getBiome(pos);
            var biomeKey = biomeEntry.getKey();
            if (biomeKey.isPresent()) {
                String biomeName = biomeKey.get().getValue().getPath();
                context.append(String.format("- 当前生物群系: %s\n", biomeName));
                context.append(getBiomeCharacteristics(biomeName));
            }
        } catch (Exception e) {
            context.append("- 生物群系: 未知\n");
        }
        
        // 时间和天气
        context.append("\n**环境状态**:\n");
        long timeOfDay = world.getTimeOfDay() % 24000;
        context.append(String.format("- 游戏时间: %s (%d游戏刻)\n", getTimeDescription(timeOfDay), timeOfDay));
        context.append(String.format("- 天气: %s\n", getWeatherDescription(world)));
        
        // 周围安全性
        context.append("\n**周围环境**:\n");
        if (world.getRegistryKey() == net.minecraft.world.World.NETHER) {
            context.append("- ⚠️ 下界环境：危险，注意岩浆和敌对生物\n");
        } else if (world.getRegistryKey() == net.minecraft.world.World.END) {
            context.append("- ⚠️ 末地环境：极度危险，注意末影龙和虚空\n");
        } else {
            boolean isDangerous = timeOfDay > 13000 && timeOfDay < 23000; // 夜晚
            context.append(isDangerous ? 
                "- ⚠️ 夜晚时间：怪物活跃，建议寻找安全场所\n" : 
                "- ✅ 白天时间：相对安全，适合探索和建造\n");
        }
        
        return context.toString();
    }
    
    /**
     * 获取玩家记忆上下文
     */
    private String getPlayerMemoryContext(String playerName) {
        try {
            // 使用MemoryTools获取玩家的位置记忆信息
            String locationMemory = memoryTools.listSavedLocations(playerName);
            
            // 构建记忆信息
            StringBuilder memory = new StringBuilder();
            memory.append("**玩家记忆信息**:\n");
            memory.append(locationMemory).append("\n");
            
            // 目前只支持位置记忆，偏好系统将在未来版本中添加
            memory.append("- 偏好系统开发中，当前版本仅支持位置记忆\n");
            
            return memory.toString();
            
        } catch (Exception e) {
            return "**玩家记忆信息**:\n- 记忆系统暂时无法访问\n";
        }
    }
    
    /**
     * 构建服务器整体状态
     */
    private String buildServerContext() {
        StringBuilder context = new StringBuilder();
        context.append("**服务器状态**:\n");
        
        var playerManager = server.getPlayerManager();
        int onlineCount = playerManager.getPlayerList().size();
        context.append(String.format("- 在线玩家数: %d\n", onlineCount));
        
        if (onlineCount > 0) {
            context.append("- 在线玩家: ");
            playerManager.getPlayerList().forEach(p -> 
                context.append(p.getName().getString()).append(" "));
            context.append("\n");
        }
        
        context.append(String.format("- 服务器类型: %s\n", server.isDedicated() ? "专用服务器" : "单人游戏/局域网"));
        
        return context.toString();
    }
    
    private String getBiomeCharacteristics(String biomeName) {
        return switch (biomeName.toLowerCase()) {
            case "plains", "sunflower_plains" -> "- 特征: 平坦开阔，适合建造大型建筑，村庄常见\n";
            case "forest", "birch_forest", "dark_forest" -> "- 特征: 木材丰富，适合建造木屋，注意夜晚的敌对生物\n";
            case "desert" -> "- 特征: 干燥炎热，沙石丰富，有沙漠神殿，注意缺水\n";
            case "mountains", "mountain_meadow" -> "- 特征: 地势险峻，矿物丰富，视野开阔，适合建造山顶建筑\n";
            case "ocean", "deep_ocean" -> "- 特征: 水下环境，海洋生物丰富，有海洋遗迹，需要水下呼吸\n";
            case "swamp" -> "- 特征: 湿地环境，史莱姆活跃，有女巫小屋，移动缓慢\n";
            case "taiga", "snowy_taiga" -> "- 特征: 寒冷针叶林，狼群出没，木材和雪丰富\n";
            case "jungle" -> "- 特征: 茂密丛林，移动困难，豹猫出没，有丛林神殿\n";
            case "savanna" -> "- 特征: 热带草原，金合欢木特色，适合畜牧业\n";
            case "badlands", "mesa" -> "- 特征: 荒地地形，陶瓦资源丰富，金矿较多\n";
            case "mushroom_fields" -> "- 特征: 罕见蘑菇岛，无敌对生物，蘑菇牛栖息地\n";
            default -> "- 特征: 独特的生物群系，有其特殊的资源和特点\n";
        };
    }
    
    private String getTimeDescription(long timeOfDay) {
        if (timeOfDay < 1000) return "凌晨";
        if (timeOfDay < 6000) return "上午";
        if (timeOfDay < 12000) return "下午";
        if (timeOfDay < 18000) return "傍晚";
        return "夜晚";
    }
    
    private String getWeatherDescription(ServerWorld world) {
        if (world.isThundering()) return "雷暴⛈️";
        if (world.isRaining()) return "下雨🌧️";
        return "晴朗☀️";
    }
    
    private String getWorldDisplayName(ServerWorld world) {
        if (world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == net.minecraft.world.World.NETHER) return "下界";
        if (world.getRegistryKey() == net.minecraft.world.World.END) return "末地";
        return world.getRegistryKey().getValue().toString();
    }
}