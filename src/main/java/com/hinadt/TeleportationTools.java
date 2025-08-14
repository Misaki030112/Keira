package com.hinadt;

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

/**
 * 传送工具集合
 * - 支持坐标传送
 * - 支持预定义地点传送
 * - 支持其他玩家传送
 */
public class TeleportationTools {
    
    private final MinecraftServer server;
    
    // 预定义的常用地点
    private static final Map<String, Vec3d> PREDEFINED_LOCATIONS = new HashMap<>();
    static {
        PREDEFINED_LOCATIONS.put("出生点", new Vec3d(0, 70, 0));
        PREDEFINED_LOCATIONS.put("spawn", new Vec3d(0, 70, 0));
        PREDEFINED_LOCATIONS.put("主城", new Vec3d(100, 70, 100));
        PREDEFINED_LOCATIONS.put("city", new Vec3d(100, 70, 100));
        PREDEFINED_LOCATIONS.put("矿洞", new Vec3d(0, 20, 0));
        PREDEFINED_LOCATIONS.put("mine", new Vec3d(0, 20, 0));
        PREDEFINED_LOCATIONS.put("农场", new Vec3d(-100, 70, -100));
        PREDEFINED_LOCATIONS.put("farm", new Vec3d(-100, 70, -100));
        PREDEFINED_LOCATIONS.put("海边", new Vec3d(0, 70, 500));
        PREDEFINED_LOCATIONS.put("beach", new Vec3d(0, 70, 500));
        PREDEFINED_LOCATIONS.put("山顶", new Vec3d(200, 120, 200));
        PREDEFINED_LOCATIONS.put("mountain", new Vec3d(200, 120, 200));
    }
    
    public TeleportationTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "teleport_player",
        description = """
        传送玩家到指定位置。这是一个智能传送工具，支持多种传送方式：
        
        1. 记忆位置传送：优先使用玩家之前保存的位置（如"家"、"农场"等）
        2. 坐标传送：精确坐标格式如 "100 70 200" 或 "100,70,200"
        3. 预设地点传送：系统预定义的常用位置（出生点、主城、矿洞、农场、海边、山顶）
        4. 玩家传送：传送到其他在线玩家当前位置
        5. 智能位置解析：根据描述词智能推测合适的位置（如"地下"、"天空"、"海边"等）
        6. 多世界传送：支持主世界、下界、末地之间的传送
        
        使用优先级：记忆位置 > 精确坐标 > 其他玩家位置 > 预设地点 > 智能解析
        """
    )
    public String teleportPlayer(
        @ToolParam(description = "要传送的玩家名称") String playerName,
        @ToolParam(description = "目标位置：可以是记忆中的位置名称（如'家'、'农场'）、精确坐标(x y z)、预设地点名称、其他玩家名称、或描述性位置（如'地下'、'天空'）") String destination,
        @ToolParam(description = "目标世界，可选：overworld(主世界)、nether(下界)、end(末地)，默认为玩家当前世界或记忆位置指定的世界") String world
    ) {
        // 找到目标玩家
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 错误：找不到玩家 " + playerName;
        }
        
        // 1. 优先检查记忆中的位置
        MemorySystem.LocationData savedLocation = MemorySystem.getLocationForTeleport(playerName, destination);
        if (savedLocation != null) {
            ServerWorld targetWorld = getTargetWorld(savedLocation.world);
            if (targetWorld == null) {
                targetWorld = player.getServerWorld();
            }
            return teleportToPosition(player, savedLocation.toVec3d(), targetWorld) + 
                   " (使用记忆位置：" + savedLocation.name + ")";
        }
        
        // 2. 解析其他类型的目标位置
        Vec3d targetPos = parseDestination(destination);
        if (targetPos == null) {
            // 3. 尝试作为其他玩家名称
            ServerPlayerEntity targetPlayer = findPlayer(destination);
            if (targetPlayer != null) {
                targetPos = targetPlayer.getPos();
                return teleportToPosition(player, targetPos, targetPlayer.getServerWorld()) + 
                       " (传送到玩家 " + destination + " 的位置)";
            }
            
            // 4. 智能解析位置描述
            targetPos = intelligentLocationParsing(destination);
            if (targetPos == null) {
                return "❌ 错误：无法识别目标位置 '" + destination + "'。" +
                       "请使用记忆位置名称、坐标格式(x y z)、预定义地点名称或玩家名称。" +
                       "你也可以先用\"记住这里是我的[位置名]\"保存当前位置。";
            }
        }
        
        // 获取目标世界
        ServerWorld targetWorld = getTargetWorld(world);
        if (targetWorld == null) {
            targetWorld = player.getServerWorld(); // 默认当前世界
        }
        
        return teleportToPosition(player, targetPos, targetWorld);
    }
    
    private Vec3d parseDestination(String destination) {
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
        
        // 尝试匹配预定义地点
        String lowerDest = destination.toLowerCase().trim();
        for (Map.Entry<String, Vec3d> entry : PREDEFINED_LOCATIONS.entrySet()) {
            if (entry.getKey().toLowerCase().contains(lowerDest) || 
                lowerDest.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    private Vec3d intelligentLocationParsing(String description) {
        String lower = description.toLowerCase().trim();
        
        // 基于关键词的智能匹配
        if (lower.contains("家") || lower.contains("home")) {
            return new Vec3d(0, 70, 0); // 出生点作为家
        }
        if (lower.contains("地下") || lower.contains("underground") || lower.contains("洞")) {
            return new Vec3d(0, 20, 0);
        }
        if (lower.contains("天空") || lower.contains("sky") || lower.contains("高")) {
            return new Vec3d(0, 200, 0);
        }
        if (lower.contains("水") || lower.contains("sea") || lower.contains("ocean")) {
            return new Vec3d(0, 62, 300); // 海平面
        }
        if (lower.contains("沙漠") || lower.contains("desert")) {
            return new Vec3d(500, 70, 500);
        }
        if (lower.contains("森林") || lower.contains("forest")) {
            return new Vec3d(-300, 70, -300);
        }
        if (lower.contains("雪") || lower.contains("snow") || lower.contains("冰")) {
            return new Vec3d(0, 70, -500);
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
                
                // 执行传送
                player.teleport(targetWorld, safePos.x, safePos.y, safePos.z, player.getYaw(), player.getPitch());
                
                // 发送确认消息
                String worldName = getWorldDisplayName(targetWorld);
                String message = String.format("✅ 传送成功！已将 %s 传送到 %s 的坐标 (%.1f, %.1f, %.1f)", 
                    player.getName().getString(), worldName, safePos.x, safePos.y, safePos.z);
                
                player.sendMessage(Text.of("[AI Misaki] " + message));
                result.set(message);
                
            } catch (Exception e) {
                String errorMsg = "传送失败：" + e.getMessage();
                player.sendMessage(Text.of("[AI Misaki] " + errorMsg));
                result.set(errorMsg);
                AiMisakiMod.LOGGER.error("传送玩家时出错", e);
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