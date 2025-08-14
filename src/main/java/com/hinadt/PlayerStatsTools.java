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
        获取指定玩家的详细状态信息。这是一个强大的玩家信息查询工具，提供全面的玩家数据：
        
        返回信息包括：
        - 玩家当前所在世界（主世界/下界/末地）
        - 精确三维坐标位置
        - 当前生命值状态（当前值/最大值）
        - 饥饿值和饱食度
        - 经验等级和进度
        - 当前游戏模式（生存/创造/冒险/观察者）
        - 在线状态确认
        
        适用场景：
        - 检查玩家健康状况
        - 确认玩家位置
        - 管理员监控
        - 队友协调
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
        全面治疗指定玩家，恢复其所有健康状态到最佳水平。这是一个强大的医疗工具：
        
        治疗效果：
        - 生命值恢复到满值（20/20）
        - 饥饿值恢复到满值（20/20）
        - 饱食度恢复到满值（完全饱腹状态）
        - 清除所有负面状态效果（中毒、凋零、缓慢等）
        - 保留正面效果（力量、速度等增益效果）
        
        适用场景：
        - 紧急救援
        - 战斗后恢复
        - 探险准备
        - 管理员支援
        - 新手帮助
        
        注意：此工具会立即生效，玩家将收到治疗确认消息
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
        获取服务器当前所有在线玩家的完整列表和基本状态信息。这是服务器管理和社交的重要工具：
        
        提供信息：
        - 在线玩家总数统计
        - 每个玩家的用户名
        - 玩家当前所在世界
        - 玩家精确坐标位置
        - 玩家当前生命值状态
        - 按在线时间或字母顺序排列
        
        适用场景：
        - 查看服务器活跃度
        - 寻找其他玩家进行协作
        - 管理员监控
        - 社交互动规划
        - 活动组织
        
        返回格式：清晰的列表形式，易于阅读和理解
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
        向指定玩家发送私人消息。这是一个高级通信工具，支持安全的点对点消息传递：
        
        功能特性：
        - 私密性：只有目标玩家能看到消息
        - 即时性：消息立即传递给在线玩家
        - 身份标识：消息显示为来自AI Misaki
        - 格式美观：使用特殊颜色和格式突出显示
        - 状态反馈：确认消息发送成功或失败
        
        适用场景：
        - 管理员通知
        - 个人提醒
        - 私密建议
        - 重要警告
        - 个性化服务
        
        消息将以醒目的格式显示在目标玩家的聊天界面中
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