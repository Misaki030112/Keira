package com.hinadt.ai;

import com.hinadt.AiMisakiMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MOD管理员权限系统
 * 基于数据库的权限管理，支持层级权限控制
 */
public class ModAdminSystem {
    
    private Connection connection;
    private MinecraftServer server;
    
    /**
     * 权限级别定义
     */
    public enum PermissionLevel {
        USER(0, "普通用户"),
        MOD_ADMIN(2, "MOD管理员"),
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
    
    public ModAdminSystem(MinecraftServer server, Connection dbConnection) {
        this.server = server;
        this.connection = dbConnection;
    }
    
    /**
     * 获取玩家的权限级别
     * 优先级：数据库MOD管理员 > 服务器OP > 普通用户
     */
    public PermissionLevel getPlayerPermission(String playerName) {
        // 检查服务器OP权限
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player != null && isServerAdmin(player)) {
            return PermissionLevel.SERVER_ADMIN;
        }
        
        // 检查数据库中的MOD管理员权限
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("mod_admins.sql", "检查玩家是否为MOD管理员"))) {
            
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                int level = rs.getInt("permission_level");
                return PermissionLevel.fromLevel(level);
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("检查MOD管理员权限失败", e);
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
     * 添加MOD管理员
     */
    public boolean addModAdmin(String playerName, PermissionLevel level, String grantedBy, String notes) {
        if (level == PermissionLevel.USER) {
            return false; // 不能设置为普通用户权限
        }
        
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("mod_admins.sql", "添加MOD管理员"))) {
            
            stmt.setString(1, playerName);
            stmt.setInt(2, level.getLevel());
            stmt.setString(3, grantedBy);
            stmt.setString(4, notes);
            
            int result = stmt.executeUpdate();
            if (result > 0) {
                AiMisakiMod.LOGGER.info("添加MOD管理员: {} (级别: {}, 授权者: {})", playerName, level.getDisplayName(), grantedBy);
                return true;
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("添加MOD管理员失败", e);
        }
        
        return false;
    }
    
    /**
     * 移除MOD管理员权限
     */
    public boolean removeModAdmin(String playerName) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("mod_admins.sql", "移除MOD管理员权限"))) {
            
            stmt.setString(1, playerName);
            
            int result = stmt.executeUpdate();
            if (result > 0) {
                AiMisakiMod.LOGGER.info("移除MOD管理员: {}", playerName);
                return true;
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("移除MOD管理员失败", e);
        }
        
        return false;
    }
    
    /**
     * 更新管理员权限级别
     */
    public boolean updatePermissionLevel(String playerName, PermissionLevel newLevel, String notes) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("mod_admins.sql", "更新管理员权限级别"))) {
            
            stmt.setInt(1, newLevel.getLevel());
            stmt.setString(2, notes);
            stmt.setString(3, playerName);
            
            int result = stmt.executeUpdate();
            if (result > 0) {
                AiMisakiMod.LOGGER.info("更新MOD管理员权限: {} -> {}", playerName, newLevel.getDisplayName());
                return true;
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("更新管理员权限失败", e);
        }
        
        return false;
    }
    
    /**
     * 获取所有活跃的MOD管理员
     */
    public List<ModAdminInfo> getAllModAdmins() {
        List<ModAdminInfo> admins = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("mod_admins.sql", "获取所有活跃的MOD管理员"))) {
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                admins.add(new ModAdminInfo(
                    rs.getString("player_name"),
                    PermissionLevel.fromLevel(rs.getInt("permission_level")),
                    rs.getString("granted_by"),
                    rs.getTimestamp("granted_at").toLocalDateTime(),
                    rs.getString("notes")
                ));
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取MOD管理员列表失败", e);
        }
        
        return admins;
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
    
    /**
     * 获取管理员欢迎信息
     */
    public String getAdminWelcomeInfo(String playerName) {
        PermissionLevel level = getPlayerPermission(playerName);
        
        if (level == PermissionLevel.USER) {
            return "";
        }
        
        String adminType = level == PermissionLevel.SERVER_ADMIN ? "服务器管理员" : "MOD管理员";
        
        return String.format("""
            🛡️ %s %s，欢迎回来！
            
            作为%s，您拥有以下权限：
            %s
            
            💡 所有特权操作都需要您的身份验证
            """, 
            playerName, 
            adminType,
            adminType,
            level == PermissionLevel.SERVER_ADMIN ? getServerAdminFeatures() : getModAdminFeatures()
        );
    }
    
    private String getServerAdminFeatures() {
        return """
            🔧 服务器管理 - 踢人、封禁、重启服务器、传送控制
            🌤️ 世界控制 - 天气、时间、世界边界设置
            📢 系统管理 - 自动消息系统开关、全服公告
            ⚙️ MOD配置 - AI系统参数调整、功能启用/禁用
            📊 监控面板 - 玩家行为分析、服务器性能监控
            🔒 权限管理 - MOD管理员权限设置和管理
            """;
    }
    
    private String getModAdminFeatures() {
        return """
            📢 消息管理 - 自动消息系统控制
            ⚙️ AI配置 - 部分AI系统设置
            📊 数据查看 - 基础统计和监控信息
            🔍 内容审核 - 聊天内容和行为监控
            """;
    }
    
    /**
     * MOD管理员信息数据类
     */
    public static class ModAdminInfo {
        public final String playerName;
        public final PermissionLevel permissionLevel;
        public final String grantedBy;
        public final java.time.LocalDateTime grantedAt;
        public final String notes;
        
        public ModAdminInfo(String playerName, PermissionLevel permissionLevel, String grantedBy, 
                          java.time.LocalDateTime grantedAt, String notes) {
            this.playerName = playerName;
            this.permissionLevel = permissionLevel;
            this.grantedBy = grantedBy;
            this.grantedAt = grantedAt;
            this.notes = notes;
        }
    }
}