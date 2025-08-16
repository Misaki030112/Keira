package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.persistence.MyBatisSupport;
import com.hinadt.persistence.mapper.ModAdminMapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;


/**
 * MOD管理员权限系统
 * 基于数据库的权限管理，支持层级权限控制
 */
public class ModAdminSystem {

    private final MinecraftServer server;
    
    /**
     * 权限级别定义
     */
    public enum PermissionLevel {
        USER(0, "普通用户"),
        MOD_ADMIN(2, "Ausuak.Ai MOD管理员"),
        SERVER_ADMIN(4, "服务器管理员");
        
        private final int level;
        private final String displayName;
        
        PermissionLevel(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }
        
        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        
        public boolean hasPermission(PermissionLevel required) {
            return this.level >= required.level;
        }
        
        public static PermissionLevel fromLevel(int level) {
            for (PermissionLevel perm : values()) {
                if (perm.level == level) return perm;
            }
            return USER;
        }
    }
    
    public ModAdminSystem(MinecraftServer server) {
        this.server = server;
    }
    
    /**
     * 获取玩家的权限级别
     * 优先级：数据库MOD管理员 > 服务器OP > 普通用户
     */
    public PermissionLevel getPlayerPermission(String playerName) {
        // 检查服务器OP权限
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (isServerAdmin(player)) {
            return PermissionLevel.SERVER_ADMIN;
        }
        
        // 检查数据库中的MOD管理员权限
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ModAdminMapper mapper = session.getMapper(ModAdminMapper.class);
            Integer level = mapper.getPermissionLevel(playerName);
            if (level != null) return PermissionLevel.fromLevel(level);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("检查MOD管理员权限失败", e);
        }
        
        return PermissionLevel.USER;
    }
    
    /**
     * 检查玩家是否为服务器管理员（OP）
     */
    private boolean isServerAdmin(ServerPlayerEntity player) {
        if (player == null) return false;
        
        // 方法1：检查是否是操作员
        boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
        
        // 方法2：单人游戏模式下，玩家默认拥有管理员权限
        boolean isSinglePlayer = !server.isDedicated();
        
        return isOp || isSinglePlayer;
    }
    
    /**
     * 检查权限并返回结果消息
     */
    public String checkPermissionWithMessage(String playerName, PermissionLevel requiredLevel, String operation) {
        PermissionLevel playerLevel = getPlayerPermission(playerName);
        
        if (playerLevel.hasPermission(requiredLevel)) {
            return "PERMISSION_GRANTED"; // 特殊返回值，表示权限验证通过
        } else {
            return generatePermissionDeniedMessage(playerName, operation, playerLevel, requiredLevel);
        }
    }
    
    /**
     * 生成权限不足的友好提示消息
     */
    private String generatePermissionDeniedMessage(String playerName, String operation, 
                                                 PermissionLevel currentLevel, PermissionLevel requiredLevel) {
        String[] friendlyMessages = {
            "抱歉 " + playerName + "，" + operation + " 需要 " + requiredLevel.getDisplayName() + " 权限哦~ 🔒",
            playerName + " 你想" + operation + "？这个功能需要 " + requiredLevel.getDisplayName() + " 权限呢 😅",
            "哎呀 " + playerName + "，" + operation + " 是 " + requiredLevel.getDisplayName() + " 专用功能，你现在的权限是 " + currentLevel.getDisplayName() + " 🚫",
            playerName + "，虽然我很想帮你" + operation + "，但这需要 " + requiredLevel.getDisplayName() + " 权限才行~ 💭",
            "不好意思 " + playerName + "，" + operation + " 这种重要操作只能由 " + requiredLevel.getDisplayName() + " 执行呢 🛡️"
        };
        
        // 随机选择一条友好的拒绝消息
        int index = (int) (Math.random() * friendlyMessages.length);
        return friendlyMessages[index];
    }
}
