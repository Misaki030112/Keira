package com.hinadt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 玩家状态工具
 * 提供玩家信息查询和状态管理功能
 */
public class PlayerStatsTools {
    
    private final MinecraftServer server;
    
    public PlayerStatsTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "get_player_info",
        description = """
        获取玩家的详细信息，包括位置、生命值、饥饿值、经验等。
        """
    )
    public String getPlayerInfo(
        @ToolParam(description = "玩家名称") String playerName
    ) {
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            StringBuilder info = new StringBuilder();
            info.append("📊 玩家信息：").append(player.getName().getString()).append("\n");
            info.append("🌍 世界：").append(getWorldDisplayName(player.getServerWorld())).append("\n");
            info.append("📍 坐标：(").append(player.getBlockPos().getX())
                .append(", ").append(player.getBlockPos().getY())
                .append(", ").append(player.getBlockPos().getZ()).append(")\n");
            info.append("❤️ 生命值：").append((int)player.getHealth()).append("/20\n");
            info.append("🍗 饥饿值：").append(player.getHungerManager().getFoodLevel()).append("/20\n");
            info.append("⭐ 经验等级：").append(player.experienceLevel).append("\n");
            info.append("🎮 游戏模式：").append(player.interactionManager.getGameMode().getName()).append("\n");
            
            result.set(info.toString());
        });
        
        return result.get();
    }
    
    @Tool(
        name = "heal_player",
        description = """
        治疗玩家，恢复生命值和饥饿值到最大值。
        """
    )
    public String healPlayer(
        @ToolParam(description = "要治疗的玩家名称") String playerName
    ) {
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            try {
                // 恢复生命值
                player.setHealth(player.getMaxHealth());
                // 恢复饥饿值
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0f);
                // 清除负面效果
                player.clearStatusEffects();
                
                String message = "✅ 已治疗玩家 " + player.getName().getString() + "（生命值和饥饿值已恢复满值）";
                player.sendMessage(Text.of("§b[AI Misaki] §f" + message));
                result.set(message);
                
            } catch (Exception e) {
                String errorMsg = "❌ 治疗失败：" + e.getMessage();
                result.set(errorMsg);
                AiMisakiMod.LOGGER.error("治疗玩家时出错", e);
            }
        });
        
        return result.get();
    }
    
    @Tool(
        name = "list_online_players",
        description = """
        列出当前在线的所有玩家及其基本信息。
        """
    )
    public String listOnlinePlayers() {
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            
            if (players.isEmpty()) {
                result.set("📭 当前没有在线玩家");
                return;
            }
            
            StringBuilder info = new StringBuilder();
            info.append("👥 在线玩家列表 (").append(players.size()).append("人)：\n");
            
            for (ServerPlayerEntity player : players) {
                info.append("• ").append(player.getName().getString())
                    .append(" - ").append(getWorldDisplayName(player.getServerWorld()))
                    .append(" (").append(player.getBlockPos().getX())
                    .append(", ").append(player.getBlockPos().getY())
                    .append(", ").append(player.getBlockPos().getZ()).append(")")
                    .append(" ❤️").append((int)player.getHealth()).append("/20")
                    .append("\n");
            }
            
            result.set(info.toString());
        });
        
        return result.get();
    }
    
    @Tool(
        name = "send_message_to_player",
        description = """
        向指定玩家发送私人消息。
        """
    )
    public String sendMessageToPlayer(
        @ToolParam(description = "目标玩家名称") String targetPlayer,
        @ToolParam(description = "要发送的消息内容") String message
    ) {
        ServerPlayerEntity player = findPlayer(targetPlayer);
        if (player == null) {
            return "❌ 找不到玩家：" + targetPlayer;
        }
        
        AtomicReference<String> result = new AtomicReference<>("");
        
        runOnMainAndWait(() -> {
            try {
                player.sendMessage(Text.of("§e[AI Misaki 私信] §f" + message));
                result.set("✅ 已向 " + targetPlayer + " 发送私信：" + message);
                
            } catch (Exception e) {
                String errorMsg = "❌ 发送消息失败：" + e.getMessage();
                result.set(errorMsg);
                AiMisakiMod.LOGGER.error("发送消息时出错", e);
            }
        });
        
        return result.get();
    }
    
    private String getWorldDisplayName(net.minecraft.server.world.ServerWorld world) {
        if (world.getRegistryKey() == net.minecraft.world.World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == net.minecraft.world.World.NETHER) return "下界";
        if (world.getRegistryKey() == net.minecraft.world.World.END) return "末地";
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
        if (server.isOnThread()) {
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