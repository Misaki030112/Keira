package com.hinadt.chat;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.tools.Messages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI驱动的智能自动消息系统
 * 基于世界状态和玩家情况生成智能提示和建议
 */
@SuppressWarnings("resource")
public class IntelligentAutoMessageSystem {
    
    private static MinecraftServer server;
    private static ScheduledExecutorService scheduler;
    private static boolean systemEnabled = true;
    private static final ConcurrentHashMap<String, Boolean> playerOptOut = new ConcurrentHashMap<>();
    
    // 消息发送间隔（分钟）
    private static final int BROADCAST_INTERVAL = 15; // 全服广播间隔
    private static final int PERSONAL_INTERVAL = 10;  // 个人消息间隔
    
    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        scheduler = Executors.newScheduledThreadPool(2);
        
        // 定期发送AI驱动的广播消息
        scheduler.scheduleAtFixedRate(
            IntelligentAutoMessageSystem::sendAiBroadcastMessage, 
            BROADCAST_INTERVAL, 
            BROADCAST_INTERVAL, 
            TimeUnit.MINUTES
        );
        
        // 定期发送个性化消息
        scheduler.scheduleAtFixedRate(
            IntelligentAutoMessageSystem::sendPersonalizedMessages,
            PERSONAL_INTERVAL,
            PERSONAL_INTERVAL,
            TimeUnit.MINUTES
        );
        
        AusukaAiMod.LOGGER.info("智能自动消息系统已启动！");
    }
    
    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
    
    /**
     * 发送AI驱动的全服广播消息
     */
    private static void sendAiBroadcastMessage() {
        if (!systemEnabled || !AiRuntime.isReady() || server.getPlayerManager().getPlayerList().isEmpty()) {
            return;
        }
        
        try {
            String worldContext = gatherWorldContext();
            String broadcastPrompt = String.format("""
                作为Minecraft服务器的AI助手，生成一条有趣且有用的全服广播消息。
                
                当前服务器状态：
                %s
                
                请生成一条消息，可以包括：
                1. 基于当前世界状态的建议（天气、时间等）
                2. 游戏技巧分享
                3. 鼓励性的话语
                4. 有趣的游戏事实
                5. 活动建议
                
                消息要求：
                - 简洁有趣，不超过100字
                - 与当前游戏状态相关
                - 用中文
                - 包含合适的emoji
                - 不要重复之前的内容
                """, worldContext);
            
            long start = System.currentTimeMillis();
            AusukaAiMod.LOGGER.info("AI广播请求开始: prompt='{}'", broadcastPrompt);

            String message = AiRuntime.AIClient
                .prompt()
                .system("你是 Ausuka.ai：负责生成简洁、有趣、与当前服务器状态相关的全服广播消息，使用中文和合适的emoji。")
                .user(broadcastPrompt)
                .call()
                .content();

            long cost = System.currentTimeMillis() - start;
            if (cost > 8000) {
                AusukaAiMod.LOGGER.warn("AI广播请求完成(慢): 耗时={}ms", cost);
            } else {
                AusukaAiMod.LOGGER.info("AI广播请求完成: 耗时={}ms", cost);
            }
            
            server.execute(() -> {
                server.getPlayerManager().broadcast(
                    Text.of("§d[Ausuka.ai 💭] §f" + message), 
                    false
                );
            });
            
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("生成AI广播消息时出错", e);
        }
    }
    
    /**
     * 发送个性化消息给每个玩家
     */
    private static void sendPersonalizedMessages() {
        if (!systemEnabled || !AiRuntime.isReady()) {
            return;
        }
        
        server.getPlayerManager().getPlayerList().forEach(player -> {
            String playerName = player.getName().getString();
            
            // 检查玩家是否选择退出
            if (playerOptOut.getOrDefault(playerName, false)) {
                return;
            }
            
            // 检查玩家是否在AI聊天模式（避免打扰）
            if (AiChatSystem.isInAiChatMode(playerName)) {
                return;
            }
            
            try {
                String playerContext = gatherPlayerContext(player);
                String personalPrompt = String.format("""
                    为玩家 %s 生成一条个性化的AI提示消息。
                    
                    玩家状态：
                    %s
                    
                    请根据玩家的具体情况生成建议，可以包括：
                    1. 基于玩家位置的建议（如在地下挖矿提醒、在海边钓鱼等）
                    2. 健康状态建议（如生命值低时建议治疗）
                    3. 环境相关提示（在特定生物群系的建议）
                    4. 个性化游戏建议
                    5. 资源管理建议
                    
                    消息要求：
                    - 简洁实用，不超过80字
                    - 针对性强，与玩家当前状态相关
                    - 用中文
                    - 友好亲切的语调
                    - 包含实用的游戏建议
                    """, playerName, playerContext);
                
                long start = System.currentTimeMillis();
                AusukaAiMod.LOGGER.info("AI个性化请求开始: 玩家={}, prompt='{}'", playerName, personalPrompt);

                String message = AiRuntime.AIClient
                    .prompt()
                    .system("你是 Ausuka.ai：为特定玩家生成简洁、实用、与其当前状态相关的个性化中文建议，不超过80字，包含emoji。")
                    .user(personalPrompt)
                    .call()
                    .content();

                long cost = System.currentTimeMillis() - start;
                if (cost > 8000) {
                    AusukaAiMod.LOGGER.warn("AI个性化请求完成(慢): 玩家={}, 耗时={}ms", playerName, cost);
                } else {
                    AusukaAiMod.LOGGER.info("AI个性化请求完成: 玩家={}, 耗时={}ms", playerName, cost);
                }
                
                server.execute(() -> {
                    Messages.to(player, Text.of("§e[Ausuka.ai 💡] §f" + message));
                });
                
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("生成个性化消息时出错: " + playerName, e);
            }
        });
    }
    
    /**
     * 收集世界上下文信息
     */
    private static String gatherWorldContext() {
        StringBuilder context = new StringBuilder();
        
        context.append("在线玩家数: ").append(server.getPlayerManager().getPlayerList().size()).append("\n");
        
        // 获取主世界信息
            var overworld = server.getWorld(World.OVERWORLD);
        if (overworld != null) {
            long timeOfDay = overworld.getTimeOfDay() % 24000;
            String timeDesc = getTimeDescription(timeOfDay);
            context.append("主世界时间: ").append(timeDesc).append("\n");
            
            boolean isRaining = overworld.isRaining();
            boolean isThundering = overworld.isThundering();
            String weather = isThundering ? "雷雨" : (isRaining ? "下雨" : "晴朗");
            context.append("天气: ").append(weather).append("\n");
        }
        
        // 玩家分布信息
        long playersInOverworld = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.OVERWORLD)
            .count();
        long playersInNether = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.NETHER)
            .count();
        long playersInEnd = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.END)
            .count();
            
        context.append(String.format("玩家分布 - 主世界:%d, 下界:%d, 末地:%d", 
            playersInOverworld, playersInNether, playersInEnd));
        
        return context.toString();
    }
    
    /**
     * 收集玩家上下文信息
     */
    private static String gatherPlayerContext(ServerPlayerEntity player) {
        StringBuilder context = new StringBuilder();
        
        // 基本信息
        BlockPos pos = player.getBlockPos();
        String worldName = getWorldDisplayName(player.getWorld());
        context.append(String.format("位置: (%d, %d, %d) 在%s\n", 
            pos.getX(), pos.getY(), pos.getZ(), worldName));
        
        // 健康状态
        int health = (int) player.getHealth();
        int hunger = player.getHungerManager().getFoodLevel();
        context.append(String.format("生命值: %d/20, 饥饿值: %d/20\n", health, hunger));
        
        // Y轴位置分析
        if (pos.getY() < 0) {
            context.append("处于地下深层（基岩层附近）\n");
        } else if (pos.getY() < 32) {
            context.append("处于地下（挖矿层）\n");
        } else if (pos.getY() > 100) {
            context.append("处于高空（山顶或建筑）\n");
        } else {
            context.append("处于地表\n");
        }
        
        // 生物群系（如果可获取）
        try {
            var biome = player.getWorld().getBiome(pos);
            context.append("生物群系: ").append(biome.getKey().map(k -> k.getValue().getPath()).orElse("未知")).append("\n");
        } catch (Exception ignored) {}
        
        // 经验等级
        context.append("经验等级: ").append(player.experienceLevel);
        
        return context.toString();
    }
    
    private static String getTimeDescription(long timeOfDay) {
        if (timeOfDay < 1000) return "凌晨";
        if (timeOfDay < 6000) return "上午";
        if (timeOfDay < 12000) return "下午";
        if (timeOfDay < 18000) return "傍晚";
        return "夜晚";
    }
    
    private static String getWorldDisplayName(ServerWorld world) {
        if (world.getRegistryKey() == World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == World.NETHER) return "下界";
        if (world.getRegistryKey() == World.END) return "末地";
        return world.getRegistryKey().getValue().toString();
    }
    
    // 命令工具：管理员控制系统
    // 管理员控制方法：启用或禁用全服自动消息系统
    public static String toggleAutoMessages(boolean enabled) {
        systemEnabled = enabled;
        String status = enabled ? "启用" : "禁用";
        return "✅ 自动消息系统已" + status;
    }
    
    // 管理员控制方法：为特定玩家启用或禁用个性化自动消息
    public static String togglePlayerAutoMessages(String playerName, boolean enabled) {
        playerOptOut.put(playerName, !enabled);
        String status = enabled ? "启用" : "禁用";
        return String.format("✅ 已为玩家 %s %s个性化自动消息", playerName, status);
    }
    
    /**
     * 检查系统是否启用
     */
    public static boolean isSystemEnabled() {
        return systemEnabled;
    }
}
