package com.keira.command.core;

import com.keira.ai.AiRuntime;
import com.keira.ai.ModAdminSystem;
import com.keira.tools.AdminTools;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Permissions {
    private Permissions() {}

    public static ModAdminSystem.PermissionLevel getPlayerPermission(ServerPlayerEntity player) {
        if (player == null) return ModAdminSystem.PermissionLevel.USER;
        if (AdminTools.isPlayerAdmin(AiServices.server(), player)) {
            return ModAdminSystem.PermissionLevel.SERVER_ADMIN;
        }
        try {
            var modAdmin = AiRuntime.getModAdminSystem();
            if (modAdmin != null) {
                return modAdmin.getPlayerPermission(player.getName().getString());
            }
        } catch (Exception ignored) {}
        return ModAdminSystem.PermissionLevel.USER;
    }

    public static boolean has(ServerPlayerEntity player, ModAdminSystem.PermissionLevel required) {
        return getPlayerPermission(player).hasPermission(required);
    }
}
