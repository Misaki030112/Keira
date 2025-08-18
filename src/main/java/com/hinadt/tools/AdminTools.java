package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.ModAdminSystem;
import com.hinadt.observability.RequestContext;
import com.hinadt.util.MainThread;
import com.hinadt.util.Messages;
import net.minecraft.registry.RegistryKey;
import com.hinadt.ai.AiRuntime;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin tools with auditing and thread safety.
 * - English logs/comments; server language en_us.
 * - All world/player mutations run on the main thread via MainThread.
 */
public class AdminTools {

    private final MinecraftServer server;
    private final ModAdminSystem modAdminSystem;

    private static final Map<String, Vec3d> frozenPlayers = new ConcurrentHashMap<>();
    private static final Map<String, JailInfo> jailedPlayers = new ConcurrentHashMap<>();

    public AdminTools(MinecraftServer server, ModAdminSystem modAdminSystem) {
        this.server = server;
        this.modAdminSystem = modAdminSystem;
    }

    /** Check if a player is an administrator (OP or singleplayer host). */
    public static boolean isPlayerAdmin(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return false;
        boolean isOp = server.getPlayerManager().isOperator(player.getGameProfile());
        boolean isSinglePlayer = !server.isDedicated();
        return isOp || isSinglePlayer;
    }

    // ---- Kick ----

    @Tool(name = "kick_player",
          description = "Kick a player from the server immediately. Admin only. Reason is shown to the player.")
    public String kickPlayer(
            @ToolParam(description = "Admin name performing the action") String adminName,
            @ToolParam(description = "Target player name") String targetPlayerName,
            @ToolParam(description = "Reason (optional)") String reason
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:kick_player] admin='{}' target='{}' reason='{}'",
                RequestContext.midTag(), adminName, targetPlayerName, reason);
        String permission = modAdminSystem.checkPermissionWithMessage(adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "kick player");
        if (!"PERMISSION_GRANTED".equals(permission)) return permission;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
        if (target == null) return "❌ Player not found: " + targetPlayerName;
        if (isPlayerAdmin(server, target)) return "❌ Cannot kick another admin.";

        String msg = (reason == null || reason.isBlank()) ? "Kicked by admin" : reason;
        MainThread.runSync(server, () -> target.networkHandler.disconnect(Text.translatable("admin.kick.disconnect", msg)));
        Messages.broadcast(server, Text.translatable("admin.kick.broadcast", targetPlayerName, adminName, msg));
        return String.format("✅ Kicked %s: %s", targetPlayerName, msg);
    }

    // ---- Ban ----

    @Tool(name = "ban_player",
          description = "Ban a player from rejoining. Admin only. If online, they are disconnected.")
    public String banPlayer(
            @ToolParam(description = "Admin name performing the action") String adminName,
            @ToolParam(description = "Target player name") String targetPlayerName,
            @ToolParam(description = "Ban reason (optional)") String reason
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:ban_player] admin='{}' target='{}' reason='{}'",
                RequestContext.midTag(), adminName, targetPlayerName, reason);
        String permission = modAdminSystem.checkPermissionWithMessage(adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "ban player");
        if (!"PERMISSION_GRANTED".equals(permission)) return permission;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
        if (target != null && isPlayerAdmin(server, target)) return "❌ Cannot ban another admin.";

        String msg = (reason == null || reason.isBlank()) ? "Violation of server rules" : reason;
        try {
            ServerCommandSource source = server.getCommandSource();
            String command = String.format("ban %s %s", targetPlayerName, msg);
            server.getCommandManager().executeWithPrefix(source, command);
            if (target != null) {
                MainThread.runSync(server, () -> target.networkHandler.disconnect(Text.translatable("admin.ban.disconnect", msg)));
            }
            Messages.broadcast(server, Text.translatable("admin.ban.broadcast", targetPlayerName, adminName, msg));
            return String.format("✅ Banned %s: %s", targetPlayerName, msg);
        } catch (Exception e) {
            return "❌ Ban failed: " + e.getMessage();
        }
    }

    // ---- Freeze ----

    @Tool(name = "freeze_player",
          description = "Freeze or unfreeze a player (cannot move). Admin only.")
    public String freezePlayer(
            @ToolParam(description = "Admin name performing the action") String adminName,
            @ToolParam(description = "Target player name") String targetPlayerName,
            @ToolParam(description = "true=freeze, false=unfreeze") boolean freeze
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:freeze_player] admin='{}' target='{}' freeze={}",
                RequestContext.midTag(), adminName, targetPlayerName, freeze);
        String permission = modAdminSystem.checkPermissionWithMessage(adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "freeze player");
        if (!"PERMISSION_GRANTED".equals(permission)) return permission;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
        if (target == null) return "❌ Player not found: " + targetPlayerName;
        if (isPlayerAdmin(server, target)) return "❌ Cannot freeze another admin.";

        if (freeze) {
            Vec3d pos = target.getPos();
            frozenPlayers.put(targetPlayerName, pos);
            MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.freeze.frozen")));
            return String.format("✅ Frozen %s", targetPlayerName);
        } else {
            frozenPlayers.remove(targetPlayerName);
            MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.freeze.unfrozen")));
            return String.format("✅ Unfrozen %s", targetPlayerName);
        }
    }

    // ---- Force Teleport ----

    @Tool(name = "teleport_player_force",
          description = "Force teleport a player to coordinates or another player's location. Admin only.")
    public String forcePlayerTeleport(
            @ToolParam(description = "Admin name performing the action") String adminName,
            @ToolParam(description = "Target player name") String targetPlayerName,
            @ToolParam(description = "Target X (optional when teleporting to player)") Double x,
            @ToolParam(description = "Target Y (optional when teleporting to player)") Double y,
            @ToolParam(description = "Target Z (optional when teleporting to player)") Double z,
            @ToolParam(description = "Destination player name (optional when using coordinates)") String targetLocation
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:teleport_player_force] admin='{}' target='{}' xyz=({}, {}, {}) destPlayer='{}'",
                RequestContext.midTag(), adminName, targetPlayerName, x, y, z, targetLocation);
        String permission = modAdminSystem.checkPermissionWithMessage(adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "force teleport player");
        if (!"PERMISSION_GRANTED".equals(permission)) return permission;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
        if (target == null) return "❌ Player not found: " + targetPlayerName;

        try {
            if (targetLocation != null && !targetLocation.isBlank()) {
                ServerPlayerEntity dest = server.getPlayerManager().getPlayer(targetLocation);
                if (dest == null) return "❌ Destination player not found: " + targetLocation;
                MainThread.runSync(server, () -> target.teleport(dest.getWorld(), dest.getX(), dest.getY(), dest.getZ(), Set.of(), dest.getYaw(), dest.getPitch(), false));
                MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.teleport.to_player", dest.getName().getString())));
                return String.format("✅ Teleported %s to %s", targetPlayerName, targetLocation);
            }
            if (x != null && y != null && z != null) {
                double tx = x, ty = y, tz = z;
                MainThread.runSync(server, () -> target.teleport(target.getWorld(), tx, ty, tz, Set.of(), target.getYaw(), target.getPitch(), false));
                String sx = String.format("%.1f", tx), sy = String.format("%.1f", ty), sz = String.format("%.1f", tz);
                MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.teleport.to_coords", sx, sy, sz)));
                return String.format("✅ Teleported %s to (%.1f, %.1f, %.1f)", targetPlayerName, tx, ty, tz);
            }
            return "❌ Provide coordinates or destination player name.";
        } catch (Exception e) {
            return "❌ Teleport failed: " + e.getMessage();
        }
    }

    // ---- Jail ----

    @Tool(name = "jail_player",
          description = "Jail or release a player. Admin only. Jail requires a saved 'jail' location via memory tools.")
    public String jailPlayer(
            @ToolParam(description = "Admin name performing the action") String adminName,
            @ToolParam(description = "Target player name") String targetPlayerName,
            @ToolParam(description = "true=jail, false=release") boolean jail,
            @ToolParam(description = "Jail location name (optional, default 'jail')") String jailLocationName,
            @ToolParam(description = "Reason (optional)") String reason
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:jail_player] admin='{}' target='{}' jail={} reason='{}'",
                RequestContext.midTag(), adminName, targetPlayerName, jail, reason);
        String permission = modAdminSystem.checkPermissionWithMessage(adminName, ModAdminSystem.PermissionLevel.MOD_ADMIN, "jail player");
        if (!"PERMISSION_GRANTED".equals(permission)) return permission;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetPlayerName);
        if (target == null) return "❌ Player not found: " + targetPlayerName;
        if (isPlayerAdmin(server, target)) return "❌ Cannot jail another admin.";

        if (jail) {
            String name = (jailLocationName == null || jailLocationName.isBlank()) ? "jail" : jailLocationName.trim();
            // Prefer server-wide memory, then admin's memory
            var loc = AiRuntime.getConversationMemory().getLocationForTeleport("server", name);
            if (loc == null) {
                loc = AiRuntime.getConversationMemory().getLocationForTeleport(adminName, name);
            }
            if (loc == null) {
                return "❌ Jail location not found. Use memory tools to save a location named '" + name + "' (playerName='server' recommended).";
            }

            // Resolve world by id
            Identifier dimId = Identifier.tryParse(loc.world());
            ServerWorld world = null;
            if (dimId != null) {
                for (ServerWorld w : server.getWorlds()) {
                    if (w.getRegistryKey().getValue().equals(dimId)) { world = w; break; }
                }
            }
            if (world == null) world = target.getWorld();

            Vec3d originalPos = target.getPos();
            RegistryKey<World> originalDim = target.getWorld().getRegistryKey();
            jailedPlayers.put(targetPlayerName, new JailInfo(originalPos, originalDim, (reason == null ? "unspecified" : reason)));

            double jx = loc.x(), jy = loc.y(), jz = loc.z();
            ServerWorld finalWorld = world;
            MainThread.runSync(server, () -> target.teleport(finalWorld, jx, jy, jz, Set.of(), target.getYaw(), target.getPitch(), false));
            // Freeze after jailing
            frozenPlayers.put(targetPlayerName, new Vec3d(jx, jy, jz));
            MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.jail.jailed")));
            MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.freeze.frozen")));
            return String.format("✅ Jailed %s at %s", targetPlayerName, name);
        } else {
            JailInfo info = jailedPlayers.remove(targetPlayerName);
            if (info == null) return "❌ Player is not jailed: " + targetPlayerName;
            ServerWorld world = server.getWorld(info.originalDimension);
            if (world == null) world = target.getWorld();
            Vec3d pos = info.originalPosition;
            ServerWorld finalWorld = world;
            MainThread.runSync(server, () -> target.teleport(finalWorld, pos.x, pos.y, pos.z, Set.of(), target.getYaw(), target.getPitch(), false));
            frozenPlayers.remove(targetPlayerName);
            MainThread.runSync(server, () -> Messages.to(target, Text.translatable("admin.jail.released")));
            return String.format("✅ Released %s", targetPlayerName);
        }
    }

    // ---- Admin status report ----

    @Tool(name = "get_player_admin_status",
          description = "Return a human-readable admin capability summary for a player.")
    public String getPlayerAdminStatus(@ToolParam(description = "Player name") String playerName) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) return "❌ Player not found: " + playerName;
        boolean serverAdmin = isPlayerAdmin(server, player);
        ModAdminSystem.PermissionLevel level = modAdminSystem.getPlayerPermission(playerName);

        String serverType = server.isDedicated() ? "Dedicated" : "Singleplayer/LAN";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 %s Admin Status\n", playerName));
        sb.append(String.format("🖥️ Server type: %s\n", serverType));
        sb.append(String.format("👑 OP/Host: %s\n", serverAdmin ? "yes" : "no"));
        sb.append(String.format("🛡️ Mod permission: %s\n", level.name()));
        if (frozenPlayers.containsKey(playerName)) sb.append("🧊 State: frozen\n");
        if (jailedPlayers.containsKey(playerName)) sb.append("🔒 State: jailed\n");
        sb.append("\nAvailable actions depend on level and OP status.\n");
        return sb.toString();
    }

    /** Admin welcome info for chat systems. */
    public static String getAdminWelcomeInfo(String playerName) {
        return String.format("🛡️ Admin %s, welcome to Ausuka.ai. You have access to moderation tools like kick, ban, freeze, force teleport, and jail. Use responsibly.", playerName);
    }

    // ---- Internals ----

    private static class JailInfo {
        final Vec3d originalPosition;
        final RegistryKey<World> originalDimension;
        final String reason;

        JailInfo(Vec3d originalPosition, RegistryKey<World> originalDimension, String reason) {
            this.originalPosition = originalPosition;
            this.originalDimension = originalDimension;
            this.reason = reason;
        }
    }
}
