package com.hinadt.tools;

import com.hinadt.AiMisakiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.ai.ConversationMemorySystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI记忆系统工具集合
 * - 支持位置记忆和检索
 * - 支持玩家偏好存储
 * - 支持全局服务器记忆
 */
public class MemoryTools {
    
    private final MinecraftServer server;
    
    public MemoryTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "save_location",
        description = """
        保存玩家定义的重要位置到记忆系统。当玩家说"记住这里是我的家"时使用此工具。
        
        **功能详细说明**：
        - 保存玩家当前位置并关联一个有意义的名称
        - 支持覆盖更新已存在的位置名称
        - 自动记录位置的世界、坐标和描述信息
        - 用于后续的智能传送和位置回忆
        
        **使用场景**：
        - 玩家说："记住这里是我的家"
        - 玩家说："把这个位置保存为农场"
        - 玩家说："标记这里为矿井入口"
        
        **参数说明**：
        - playerName: 玩家名称
        - locationName: 位置名称(如"家"、"农场"、"矿井")
        - description: 位置描述(可选，用于更好的识别)
        """
    )
    public String saveLocation(
        @ToolParam(description = "要保存位置记忆的玩家名称") String playerName,
        @ToolParam(description = "位置名称，如'家'、'农场'、'矿井'等") String locationName,
        @ToolParam(description = "位置的详细描述(可选)") String description
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家: " + playerName;
        }
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        
        server.execute(() -> {
            try {
                BlockPos pos = player.getBlockPos();
                String worldName = player.getWorld().getRegistryKey().getValue().toString();
                
                // 如果没有提供描述，自动生成一个简单的描述
                String finalDescription = description != null && !description.trim().isEmpty() 
                    ? description 
                    : "玩家在 " + worldName + " 保存的位置";
                
                AiRuntime.getConversationMemory().saveLocation(
                    playerName, 
                    locationName, 
                    worldName, 
                    pos.getX(), 
                    pos.getY(), 
                    pos.getZ(), 
                    finalDescription
                );
                
                result.set(String.format("✅ 已保存位置记忆：%s → %s (%d, %d, %d) 在 %s", 
                    locationName, finalDescription, pos.getX(), pos.getY(), pos.getZ(), worldName));
                
            } catch (Exception e) {
                AiMisakiMod.LOGGER.error("保存位置记忆失败", e);
                result.set("❌ 保存位置记忆时发生错误: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ 保存位置记忆操作被中断";
        }
        
        return result.get();
    }
    
    @Tool(
        name = "get_saved_location",
        description = """
        获取玩家保存的特定位置信息。支持精确匹配和模糊搜索。
        
        **功能详细说明**：
        - 先尝试精确匹配位置名称
        - 如果精确匹配失败，进行模糊搜索
        - 返回位置的完整信息(坐标、世界、描述)
        - 用于传送前的位置验证或信息查询
        
        **搜索策略**：
        - 精确匹配："家" → 查找名为"家"的位置
        - 模糊匹配："农" → 可能匹配"农场"、"大农场"等
        
        **返回信息**：
        - 位置名称、世界、坐标、保存时的描述
        """
    )
    public String getSavedLocation(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "要查找的位置名称或关键词") String locationName
    ) {
        try {
            ConversationMemorySystem.LocationData location = 
                AiRuntime.getConversationMemory().getLocationForTeleport(playerName, locationName);
            
            if (location == null) {
                return String.format("❌ 未找到玩家 %s 的位置记忆: %s", playerName, locationName);
            }
            
            return String.format("📍 位置信息：\n" +
                "名称: %s\n" +
                "世界: %s\n" +
                "坐标: (%.1f, %.1f, %.1f)\n" +
                "描述: %s",
                location.name, location.world, location.x, location.y, location.z, location.description);
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("获取位置记忆失败", e);
            return "❌ 获取位置记忆时发生错误: " + e.getMessage();
        }
    }
    
    @Tool(
        name = "list_saved_locations",
        description = """
        列出玩家所有保存的位置记忆。用于帮助玩家回忆或选择传送目标。
        
        **功能详细说明**：
        - 显示玩家所有保存过的位置
        - 按保存时间倒序排列(最新的在前)
        - 包含位置名称、坐标、世界和描述信息
        - 帮助玩家记忆和选择可用的传送目标
        
        **使用场景**：
        - 玩家问："我保存了哪些位置？"
        - 玩家说："列出我的所有传送点"
        - AI需要为玩家提供可用的传送选项
        """
    )
    public String listSavedLocations(
        @ToolParam(description = "要查询位置记忆的玩家名称") String playerName
    ) {
        try {
            List<ConversationMemorySystem.LocationData> locations = 
                AiRuntime.getConversationMemory().getAllLocations(playerName);
            
            if (locations.isEmpty()) {
                return String.format("📍 玩家 %s 还没有保存任何位置记忆。\n" +
                    "可以使用 '记住这里是我的xxx' 来保存当前位置。", playerName);
            }
            
            StringBuilder result = new StringBuilder();
            result.append(String.format("📍 玩家 %s 的所有位置记忆：\n\n", playerName));
            
            for (int i = 0; i < locations.size(); i++) {
                ConversationMemorySystem.LocationData loc = locations.get(i);
                result.append(String.format("%d. **%s**\n", i + 1, loc.name));
                result.append(String.format("   坐标: (%.1f, %.1f, %.1f)\n", loc.x, loc.y, loc.z));
                result.append(String.format("   世界: %s\n", loc.world));
                result.append(String.format("   描述: %s\n\n", loc.description));
            }
            
            return result.toString();
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("列出位置记忆失败", e);
            return "❌ 获取位置记忆列表时发生错误: " + e.getMessage();
        }
    }
    
    @Tool(
        name = "delete_saved_location", 
        description = """
        删除玩家指定的位置记忆。用于清理不再需要的位置记忆。
        
        **功能详细说明**：
        - 永久删除指定名称的位置记忆
        - 删除后无法恢复，需要谨慎操作
        - 支持精确匹配位置名称
        
        **使用场景**：
        - 玩家说："删除我的旧家位置"
        - 玩家说："移除农场的位置记忆"
        - 清理过时或错误的位置记忆
        """
    )
    public String deleteSavedLocation(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "要删除的位置名称") String locationName
    ) {
        try {
            boolean deleted = AiRuntime.getConversationMemory().deleteLocation(playerName, locationName);
            
            if (deleted) {
                return String.format("✅ 已删除位置记忆：%s", locationName);
            } else {
                return String.format("❌ 未找到要删除的位置记忆：%s", locationName);
            }
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("删除位置记忆失败", e);
            return "❌ 删除位置记忆时发生错误: " + e.getMessage();
        }
    }
}