package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.persistence.record.LocationRecord;
import com.hinadt.observability.RequestContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * 传送工具集合
 * - 支持坐标传送
 * - 支持预定义地点传送
 * - 支持其他玩家传送
 */
public class TeleportationTools {
    
    private final MinecraftServer server;
    
    public TeleportationTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "teleport_player",
        description = """
        智能传送玩家到指定位置。这是一个AI驱动的传送工具，完全基于玩家记忆和智能解析：
        
        **传送优先级和策略**：
        1. **记忆位置传送** (最高优先级)：使用玩家之前保存的位置
           - 玩家说"带我回家"→查找记忆中的"家"
           - 支持模糊匹配："农场"可以匹配"我的农场"、"大农场"等
        
        2. **精确坐标传送**：支持多种坐标格式
           - "100 70 200" 或 "100,70,200" 或 "100/70/200"
           - 自动验证坐标合理性（Y坐标限制在-64到320之间）
        
        3. **玩家位置传送**：传送到其他在线玩家位置
           - 输入其他玩家的用户名进行传送
        
        4. **智能位置解析**：基于自然语言描述智能推测位置
           - "地下"→Y=20的合理地下位置
           - "天空"→Y=200的高空位置  
           - "海边"→接近海平面的位置
           - "沙漠"、"森林"、"雪地"等生物群系关键词
        
        5. **世界出生点**：作为最后的fallback选项
           - 仅当所有其他方式都失败时使用
        
        **安全特性**：
        - 自动调整不安全的Y坐标
        - 支持跨世界传送（主世界、下界、末地）
        - 传送前后的位置确认和反馈
        
        **AI使用建议**：
        - 优先查询玩家记忆位置
        - 根据上下文智能选择最合适的传送方式
        - 传送失败时提供清晰的原因和建议
        """
    )
    public String teleportPlayer(
        @ToolParam(description = "要传送的玩家名称") String playerName,
        @ToolParam(description = "目标位置：优先使用记忆中的位置名称（如'家'、'农场'），其次是精确坐标(x y z)，或其他玩家名称，或智能描述性位置（如'地下'、'天空'、'海边'）") String destination,
        @ToolParam(description = "目标世界，可选：overworld(主世界)、nether(下界)、end(末地)，默认为当前世界或记忆位置指定的世界") String world
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] params player='{}' dest='{}' world='{}'",
                RequestContext.midTag(), playerName, destination, world);
        // 找到目标玩家
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 错误：找不到玩家 " + playerName;
        }
        
        // 1. 最高优先级：检查记忆中的位置
        LocationRecord savedLocation = AiRuntime.getConversationMemory().getLocationForTeleport(playerName, destination);
        if (savedLocation != null) {
            AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] 使用记忆位置 name='{}' world='{}'",
                    RequestContext.midTag(), savedLocation.locationName(), savedLocation.world());
            ServerWorld targetWorld = getTargetWorld(savedLocation.world());
            if (targetWorld == null) {
                targetWorld = player.getWorld();
            }
            Vec3d pos = new Vec3d(savedLocation.x(), savedLocation.y(), savedLocation.z());
            return teleportToPosition(player, pos, targetWorld) + 
                   " (使用记忆位置：" + savedLocation.locationName() + ")";
        }
        
        // 2. 尝试解析为精确坐标
        Vec3d targetPos = parseCoordinates(destination);
        if (targetPos != null) {
            AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] 使用精确坐标 ({}, {}, {})",
                    RequestContext.midTag(), targetPos.x, targetPos.y, targetPos.z);
            ServerWorld targetWorld = getTargetWorld(world);
            if (targetWorld == null) {
                targetWorld = player.getWorld();
            }
            return teleportToPosition(player, targetPos, targetWorld) + " (使用精确坐标)";
        }
        
        // 3. 尝试作为其他玩家名称
        ServerPlayerEntity targetPlayer = findPlayer(destination);
        if (targetPlayer != null) {
            AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] 传送到其他玩家 destPlayer='{}'",
                    RequestContext.midTag(), destination);
            return teleportToPosition(player, targetPlayer.getPos(), targetPlayer.getWorld()) + 
                   " (传送到玩家 " + destination + " 的位置)";
        }
        
        // 4. 智能解析位置描述
        Vec3d intelligentPos = intelligentLocationParsing(destination, player);
        if (intelligentPos != null) {
            AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] 使用智能解析坐标 ({}, {}, {})",
                    RequestContext.midTag(), intelligentPos.x, intelligentPos.y, intelligentPos.z);
            ServerWorld targetWorld = getTargetWorld(world);
            if (targetWorld == null) {
                targetWorld = player.getWorld();
            }
            return teleportToPosition(player, intelligentPos, targetWorld) + " (智能解析位置)";
        }
        
        // 5. 最后尝试：世界出生点
        if (destination.toLowerCase().contains("出生") || destination.toLowerCase().contains("spawn")) {
            ServerWorld targetWorld = getTargetWorld(world);
            if (targetWorld == null) {
                targetWorld = player.getWorld();
            }
            BlockPos spawnPos = targetWorld.getSpawnPos();
            Vec3d spawnVec = new Vec3d(spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
            return teleportToPosition(player, spawnVec, targetWorld) + " (传送到世界出生点)";
        }
        
        // 无法识别位置
        return String.format("""
            ❌ 无法识别目标位置 '%s'。
            
            💡 建议：
            • 使用记忆位置：先说"记住这里是我的[位置名]"保存位置
            • 使用精确坐标：格式如 "100 70 200"
            • 传送到玩家：输入其他玩家的用户名
            • 智能描述：如"地下"、"天空"、"海边"等
            """, destination);
    }
    
    private Vec3d parseCoordinates(String destination) {
        // 尝试解析坐标格式：x y z 或 x,y,z 或 x/y/z
        Pattern coordPattern = Pattern.compile("(-?\\d+)(?:[\\s,/]+)(-?\\d+)(?:[\\s,/]+)(-?\\d+)");
        Matcher matcher = coordPattern.matcher(destination.trim());
        
        if (matcher.find()) {
            try {
                double x = Double.parseDouble(matcher.group(1));
                double y = Double.parseDouble(matcher.group(2));
                double z = Double.parseDouble(matcher.group(3));
                return new Vec3d(x, y, z);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
    
    private Vec3d intelligentLocationParsing(String description, ServerPlayerEntity player) {
        String lower = description.toLowerCase().trim();
        BlockPos currentPos = player.getBlockPos();
        
        // 基于当前位置的相对位置解析
        if (lower.contains("地下") || lower.contains("underground") || lower.contains("洞")) {
            // 在玩家当前位置下方的安全地下位置
            return new Vec3d(currentPos.getX(), Math.max(20, currentPos.getY() - 30), currentPos.getZ());
        }
        if (lower.contains("天空") || lower.contains("sky") || lower.contains("高")) {
            // 在玩家当前位置上方的天空位置
            return new Vec3d(currentPos.getX(), Math.min(250, currentPos.getY() + 50), currentPos.getZ());
        }
        if (lower.contains("水") || lower.contains("sea") || lower.contains("ocean") || lower.contains("海")) {
            // 寻找最近的海洋（这里简化为向某个方向的海平面位置）
            return new Vec3d(currentPos.getX() + 200, 62, currentPos.getZ());
        }
        
        // 生物群系相关的智能解析（基于当前位置的偏移）
        if (lower.contains("沙漠") || lower.contains("desert")) {
            return new Vec3d(currentPos.getX() + 500, 70, currentPos.getZ() + 300);
        }
        if (lower.contains("森林") || lower.contains("forest")) {
            return new Vec3d(currentPos.getX() - 300, 70, currentPos.getZ() - 200);
        }
        if (lower.contains("雪") || lower.contains("snow") || lower.contains("冰")) {
            return new Vec3d(currentPos.getX(), 70, currentPos.getZ() - 400);
        }
        if (lower.contains("山") || lower.contains("mountain") || lower.contains("hill")) {
            return new Vec3d(currentPos.getX() + 100, 120, currentPos.getZ() + 100);
        }
        
        // 通用位置描述
        if (lower.contains("远方") || lower.contains("far")) {
            return new Vec3d(currentPos.getX() + 1000, currentPos.getY(), currentPos.getZ() + 1000);
        }
        if (lower.contains("附近") || lower.contains("near")) {
            return new Vec3d(currentPos.getX() + 50, currentPos.getY(), currentPos.getZ() + 50);
        }
        
        return null;
    }
    
    private ServerWorld getTargetWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        
        String lower = worldName.toLowerCase().trim();
        switch (lower) {
            case "overworld":
            case "主世界":
            case "地上":
                return server.getWorld(World.OVERWORLD);
            case "nether":
            case "下界":
            case "地狱":
                return server.getWorld(World.NETHER);
            case "end":
            case "末地":
            case "末路之地":
                return server.getWorld(World.END);
            default:
                return null;
        }
    }
    
    private String teleportToPosition(ServerPlayerEntity player, Vec3d targetPos, ServerWorld targetWorld) {
        AtomicReference<String> result = new AtomicReference<>("传送失败");
        
        runOnMainAndWait(() -> {
            try {
                // 确保Y坐标在合理范围内
                double safeY = Math.max(-64, Math.min(320, targetPos.y));
                Vec3d safePos = new Vec3d(targetPos.x, safeY, targetPos.z);
                
                // 执行传送（1.21+ 需要传递 PositionFlag 集合和一个布尔值）
                AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] 执行传送 world='{}' pos=({},{},{})",
                        RequestContext.midTag(), getWorldDisplayName(targetWorld), safePos.x, safePos.y, safePos.z);
                player.teleport(targetWorld, safePos.x, safePos.y, safePos.z, Set.of(), player.getYaw(), player.getPitch(), false);
                
                String worldName = getWorldDisplayName(targetWorld);
                String message = String.format("Teleported %s to %s at (%.1f, %.1f, %.1f)",
                        player.getName().getString(), worldName, safePos.x, safePos.y, safePos.z);
                result.set(message);
                AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] success world='{}' pos=({},{},{})",
                        RequestContext.midTag(), worldName, safePos.x, safePos.y, safePos.z);
                
            } catch (Exception e) {
                String errorMsg = "Teleport failed: " + e.getMessage();
                result.set(errorMsg);
                AusukaAiMod.LOGGER.error("传送玩家时出错", e);
            }
        });
        
        return result.get();
    }
    
    private String getWorldDisplayName(ServerWorld world) {
        if (world.getRegistryKey() == World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == World.NETHER) return "下界";
        if (world.getRegistryKey() == World.END) return "末地";
        return world.getRegistryKey().getValue().toString();
    }
    
    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        // 先按名称
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        
        // 再按 UUID
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }
    
    private void runOnMainAndWait(Runnable task) {
        if (server.isOnThread()) { // 已在主线程
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
