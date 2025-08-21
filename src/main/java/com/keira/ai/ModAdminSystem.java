package com.keira.ai;

import com.keira.KeiraAiMod;
import com.keira.persistence.MyBatisSupport;
import com.keira.persistence.mapper.ModAdminMapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;


/**
 * MOD admin permission system.
 * Provides layered permission control with DB-backed roles and server OP.
 */
public class ModAdminSystem {

    private final MinecraftServer server;
    
    /**
     * Permission levels. Higher level implies all lower privileges.
     */
    public enum PermissionLevel {
        USER(0, "User"),
        MOD_ADMIN(2, "Keira AI Mod Admin"),
        SERVER_ADMIN(4, "Server Admin");
        
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
     * Resolve the effective permission level for a player name.
     * Strategy: compute both DB role and server OP, then return the highest level.
     */
    public PermissionLevel getPlayerPermission(String playerName) {
        PermissionLevel dbLevel = PermissionLevel.USER;
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ModAdminMapper mapper = session.getMapper(ModAdminMapper.class);
            Integer level = mapper.getPermissionLevel(playerName);
            if (level != null) {
                dbLevel = PermissionLevel.fromLevel(level);
            }
        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to query MOD admin permission from DB for '{}'.", playerName, e);
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        PermissionLevel opLevel = isServerAdmin(player) ? PermissionLevel.SERVER_ADMIN : PermissionLevel.USER;

        return dbLevel.getLevel() >= opLevel.getLevel() ? dbLevel : opLevel;
    }
    
    /**
     * Check whether a player is a server admin (OP) or singleplayer host.
     */
    private boolean isServerAdmin(ServerPlayerEntity player) {
        if (player == null) return false;
        boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
        boolean isSinglePlayer = !server.isDedicated();
        return isOp || isSinglePlayer;
    }
    
    /**
     * Check permission and return a machine-friendly message.
     * Returns "PERMISSION_GRANTED" when authorized, otherwise a concise reason in English.
     */
    public String checkPermissionWithMessage(String playerName, PermissionLevel requiredLevel, String operation) {
        PermissionLevel playerLevel = getPlayerPermission(playerName);
        if (playerLevel.hasPermission(requiredLevel)) {
            return "PERMISSION_GRANTED";
        } else {
            return generatePermissionDeniedMessage(playerName, operation, playerLevel, requiredLevel);
        }
    }
    
    /**
     * Produce a deterministic, English denial message for AI consumption.
     */
    private String generatePermissionDeniedMessage(String playerName, String operation,
                                                   PermissionLevel currentLevel, PermissionLevel requiredLevel) {
        return String.format(
                "PERMISSION_DENIED: Operation '%s' requires '%s'; current='%s'; player='%s'",
                operation,
                requiredLevel.getDisplayName(),
                currentLevel.getDisplayName(),
                playerName
        );
    }
}
