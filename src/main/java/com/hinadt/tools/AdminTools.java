package com.hinadt.tools;

import com.hinadt.ai.ModAdminSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员权限验证和管理工具
 * 提供全面的服务器管理功能，包括踢人、封禁、定身、强制传送等
 */
public class AdminTools {
    
    private final MinecraftServer server;
    private final ModAdminSystem modAdminSystem;
    
    // 冻结玩家状态存储
    private static final Map<String, Vec3d> frozenPlayers = new ConcurrentHashMap<>();
    // 监禁玩家状态存储
    private static final Map<String, JailInfo> jailedPlayers = new ConcurrentHashMap<>();
    
    public AdminTools(MinecraftServer server, ModAdminSystem modAdminSystem) {
        this.server = server;
        this.modAdminSystem = modAdminSystem;
    }
    
    /**
     * 检查玩家是否为管理员
     */
    public static boolean isPlayerAdmin(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return false;
        
        // 方法1：检查玩家是否是操作员（op）
        boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
        
        // 方法2：单人游戏模式下，玩家默认拥有管理员权限
        boolean isSinglePlayer = !server.isDedicated();
        
        return isOp || isSinglePlayer;
    }
    
    @Tool(
        name = "kick_player",
        description = """
        踢出指定玩家，将其从服务器中移除。
        只有管理员才能使用此功能。
        
        这是一个强制性操作，会立即断开玩家连接。
        可以指定踢出原因，玩家会看到该原因。
        """
    )
    public String kickPlayer(
        @ToolParam(description = "执行踢人操作的管理员名称") String adminName,
        @ToolParam(description = "要踢出的玩家名称") String targetPlayerName,
        @ToolParam(description = "踢出原因，可选") String reason
    ) {
        // 权限验证
        String permissionCheck = modAdminSystem.checkPermissionWithMessage(
            adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "踢出玩家");
        if (!"PERMISSION_GRANTED".equals(permissionCheck)) {
            return permissionCheck;
        }
        
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            return "❌ 找不到玩家：" + targetPlayerName;
        }
        
        // 不能踢出管理员
        if (isPlayerAdmin(server, targetPlayer)) {
            return "❌ 不能踢出其他管理员";
        }
        
        String kickReason = reason != null ? reason : "被管理员踢出服务器";
        
        try {
            targetPlayer.networkHandler.disconnect(Text.of("§c[系统] " + kickReason));
            
            // 广播消息
            String broadcastMsg = String.format("§e[管理] §c%s §e被 §a%s §e踢出服务器 - %s", 
                targetPlayerName, adminName, kickReason);
            server.getPlayerManager().broadcast(Text.of(broadcastMsg), false);
            
            return String.format("✅ 已踢出玩家 %s，原因：%s", targetPlayerName, kickReason);
            
        } catch (Exception e) {
            return "❌ 踢出玩家时发生错误：" + e.getMessage();
        }
    }
    
    @Tool(
        name = "ban_player",
        description = """
        封禁指定玩家，禁止其再次加入服务器。
        只有管理员才能使用此功能。
        
        被封禁的玩家将无法重新加入服务器，直到被解除封禁。
        可以指定封禁原因。
        """
    )
    public String banPlayer(
        @ToolParam(description = "执行封禁操作的管理员名称") String adminName,
        @ToolParam(description = "要封禁的玩家名称") String targetPlayerName,
        @ToolParam(description = "封禁原因，可选") String reason
    ) {
        // 权限验证
        String permissionCheck = modAdminSystem.checkPermissionWithMessage(
            adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "封禁玩家");
        if (!"PERMISSION_GRANTED".equals(permissionCheck)) {
            return permissionCheck;
        }
        
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
        
        // 不能封禁管理员
        if (targetPlayer != null && isPlayerAdmin(server, targetPlayer)) {
            return "❌ 不能封禁其他管理员";
        }
        
        String banReason = reason != null ? reason : "违反服务器规则";
        
        try {
            // 执行封禁命令
            ServerCommandSource source = server.getCommandSource();
            String command = String.format("ban %s %s", targetPlayerName, banReason);
            server.getCommandManager().executeWithPrefix(source, command);
            
            // 如果玩家在线，踢出
            if (targetPlayer != null) {
                targetPlayer.networkHandler.disconnect(Text.of("§c[系统] 你已被封禁：" + banReason));
            }
            
            // 广播消息
            String broadcastMsg = String.format("§e[管理] §c%s §e被 §a%s §e永久封禁 - %s", 
                targetPlayerName, adminName, banReason);
            server.getPlayerManager().broadcast(Text.of(broadcastMsg), false);
            
            return String.format("✅ 已封禁玩家 %s，原因：%s", targetPlayerName, banReason);
            
        } catch (Exception e) {
            return "❌ 封禁玩家时发生错误：" + e.getMessage();
        }
    }
    
    @Tool(
        name = "freeze_player",
        description = """
        冻结指定玩家，使其无法移动。
        只有管理员才能使用此功能。
        
        被冻结的玩家将无法移动，但可以聊天和观察。
        这是一个临时性的限制措施。
        """
    )
    public String freezePlayer(
        @ToolParam(description = "执行冻结操作的管理员名称") String adminName,
        @ToolParam(description = "要冻结的玩家名称") String targetPlayerName,
        @ToolParam(description = "是否冻结，true为冻结，false为解冻") boolean freeze
    ) {
        // 权限验证
        String permissionCheck = modAdminSystem.checkPermissionWithMessage(
            adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "冻结/解冻玩家");
        if (!"PERMISSION_GRANTED".equals(permissionCheck)) {
            return permissionCheck;
        }
        
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            return "❌ 找不到玩家：" + targetPlayerName;
        }
        
        // 不能冻结管理员
        if (isPlayerAdmin(server, targetPlayer)) {
            return "❌ 不能冻结其他管理员";
        }
        
        if (freeze) {
            // 冻结玩家
            Vec3d currentPos = targetPlayer.getPos();
            frozenPlayers.put(targetPlayerName, currentPos);
            
            targetPlayer.sendMessage(Text.of("§c[系统] 你已被管理员冻结，无法移动"));
            
            return String.format("✅ 已冻结玩家 %s", targetPlayerName);
        } else {
            // 解冻玩家
            frozenPlayers.remove(targetPlayerName);
            
            targetPlayer.sendMessage(Text.of("§a[系统] 你已被解除冻结，可以正常移动"));
            
            return String.format("✅ 已解除冻结玩家 %s", targetPlayerName);
        }
    }
    
    @Tool(
        name = "teleport_player_force",
        description = """
        强制传送玩家到指定位置或其他玩家身边。
        只有管理员才能使用此功能。
        
        这是一个强制性操作，玩家无法拒绝。
        可以传送到坐标或其他玩家位置。
        """
    )
    public String forcePlayerTeleport(
        @ToolParam(description = "执行传送操作的管理员名称") String adminName,
        @ToolParam(description = "要传送的玩家名称") String targetPlayerName,
        @ToolParam(description = "目标X坐标，如果传送到玩家则可选") Double x,
        @ToolParam(description = "目标Y坐标，如果传送到玩家则可选") Double y,
        @ToolParam(description = "目标Z坐标，如果传送到玩家则可选") Double z,
        @ToolParam(description = "目标玩家名称，如果传送到坐标则可选") String targetLocation
    ) {
        // 权限验证
        String permissionCheck = modAdminSystem.checkPermissionWithMessage(
            adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "强制传送玩家");
        if (!"PERMISSION_GRANTED".equals(permissionCheck)) {
            return permissionCheck;
        }
        
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            return "❌ 找不到玩家：" + targetPlayerName;
        }
        
        try {
            if (targetLocation != null) {
                // 传送到其他玩家位置
                ServerPlayerEntity destinationPlayer = server.getPlayerManager().getPlayer(targetLocation);
                if (destinationPlayer == null) {
                    return "❌ 找不到目标玩家：" + targetLocation;
                }
                
                Vec3d destPos = destinationPlayer.getPos();
                targetPlayer.teleport(destPos.x, destPos.y, destPos.z);
                
                targetPlayer.sendMessage(Text.of("§e[管理] 你被传送到 " + targetLocation + " 身边"));
                
                return String.format("✅ 已将 %s 传送到 %s 身边", targetPlayerName, targetLocation);
                
            } else if (x != null && y != null && z != null) {
                // 传送到指定坐标
                targetPlayer.teleport(x, y, z);
                
                targetPlayer.sendMessage(Text.of(String.format("§e[管理] 你被传送到坐标 (%.1f, %.1f, %.1f)", x, y, z)));
                
                return String.format("✅ 已将 %s 传送到坐标 (%.1f, %.1f, %.1f)", targetPlayerName, x, y, z);
                
            } else {
                return "❌ 请提供目标坐标或目标玩家名称";
            }
            
        } catch (Exception e) {
            return "❌ 传送失败：" + e.getMessage();
        }
    }
    
    @Tool(
        name = "jail_player",
        description = """
        将玩家关入监狱，限制其移动范围。
        只有管理员才能使用此功能。
        
        被监禁的玩家将被传送到监狱区域，无法离开。
        这是一个临时性的惩罚措施。
        """
    )
    public String jailPlayer(
        @ToolParam(description = "执行监禁操作的管理员名称") String adminName,
        @ToolParam(description = "要监禁的玩家名称") String targetPlayerName,
        @ToolParam(description = "是否监禁，true为监禁，false为释放") boolean jail,
        @ToolParam(description = "监禁原因，可选") String reason
    ) {
        // 权限验证
        String permissionCheck = modAdminSystem.checkPermissionWithMessage(
            adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "监禁/释放玩家");
        if (!"PERMISSION_GRANTED".equals(permissionCheck)) {
            return permissionCheck;
        }
        
        ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(targetPlayerName);
        if (targetPlayer == null) {
            return "❌ 找不到玩家：" + targetPlayerName;
        }
        
        // 不能监禁管理员
        if (isPlayerAdmin(server, targetPlayer)) {
            return "❌ 不能监禁其他管理员";
        }
        
        if (jail) {
            // 监禁玩家
            Vec3d originalPos = targetPlayer.getPos();
            Vec3d jailPos = new Vec3d(0, 100, 0); // 默认监狱坐标
            
            jailedPlayers.put(targetPlayerName, new JailInfo(originalPos, reason));
            
            // 传送到监狱
            targetPlayer.teleport(jailPos.x, jailPos.y, jailPos.z);
            
            String jailReason = reason != null ? reason : "违反服务器规则";
            targetPlayer.sendMessage(Text.of("§c[系统] 你已被监禁：" + jailReason));
            
            return String.format("✅ 已监禁玩家 %s，原因：%s", targetPlayerName, jailReason);
        } else {
            // 释放玩家
            JailInfo jailInfo = jailedPlayers.remove(targetPlayerName);
            if (jailInfo == null) {
                return "❌ 玩家 " + targetPlayerName + " 不在监狱中";
            }
            
            // 传送回原位置
            Vec3d originalPos = jailInfo.originalPosition;
            targetPlayer.teleport(originalPos.x, originalPos.y, originalPos.z);
            
            targetPlayer.sendMessage(Text.of("§a[系统] 你已被释放，请遵守服务器规则"));
            
            return String.format("✅ 已释放玩家 %s", targetPlayerName);
        }
    }
    
    @Tool(
        name = "get_player_admin_status",
        description = """
        获取玩家的详细权限状态信息。
        包括服务器OP权限、MOD管理员权限等。
        
        返回完整的权限级别和权限范围信息。
        """
    )
    public String getPlayerAdminStatus(
        @ToolParam(description = "要查询的玩家名称") String playerName
    ) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ 找不到玩家：" + playerName;
        }
        
        boolean isServerAdmin = isPlayerAdmin(server, player);
        ModAdminSystem.PermissionLevel modPermission = modAdminSystem.getPlayerPermission(playerName);
        
        String serverType = server.isDedicated() ? "专用服务器" : "单人游戏/局域网";
        
        StringBuilder status = new StringBuilder();
        status.append(String.format("📊 %s 权限状态报告\n", playerName));
        status.append(String.format("🖥️ 服务器类型：%s\n", serverType));
        status.append(String.format("👑 服务器管理员(OP)：%s\n", isServerAdmin ? "✅ 是" : "❌ 否"));
        status.append(String.format("🛡️ MOD权限级别：%s\n", modPermission.getDisplayName()));
        
        if (frozenPlayers.containsKey(playerName)) {
            status.append("🧊 当前状态：被冻结\n");
        }
        
        if (jailedPlayers.containsKey(playerName)) {
            JailInfo jailInfo = jailedPlayers.get(playerName);
            status.append(String.format("🔒 当前状态：被监禁 - %s\n", jailInfo.reason));
        }
        
        status.append("\n可执行操作：\n");
        if (modPermission == ModAdminSystem.PermissionLevel.USER) {
            status.append("• 基础AI功能\n");
        } else {
            status.append("• AI管理功能\n");
            status.append("• 自动消息系统控制\n");
            if (isServerAdmin) {
                status.append("• 服务器管理功能\n");
                status.append("• 玩家管理（踢人、封禁、监禁等）\n");
                status.append("• 世界控制功能\n");
            }
        }
        
        return status.toString();
    }
    
    /**
     * 检查玩家是否被冻结（供其他系统调用）
     */
    public static boolean isPlayerFrozen(String playerName) {
        return frozenPlayers.containsKey(playerName);
    }
    
    /**
     * 检查玩家是否被监禁（供其他系统调用）
     */
    public static boolean isPlayerJailed(String playerName) {
        return jailedPlayers.containsKey(playerName);
    }
    
    /**
     * 获取管理员欢迎信息（供聊天系统调用）
     */
    public static String getAdminWelcomeInfo(String playerName) {
        return String.format("🛡️ 管理员 %s，欢迎使用AI助手管理系统！\n" +
            "你拥有以下额外权限：踢人、封禁、冻结、强制传送、监禁等功能。\n" +
            "使用时请谨慎，确保服务器秩序和玩家体验。", playerName);
    }
    
    /**
     * 监禁信息数据类
     */
    private static class JailInfo {
        public final Vec3d originalPosition;
        public final String reason;
        
        public JailInfo(Vec3d originalPosition, String reason) {
            this.originalPosition = originalPosition;
            this.reason = reason != null ? reason : "未指定原因";
        }
    }
}