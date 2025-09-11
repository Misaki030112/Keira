package com.keira.tools;

import com.keira.KeiraAiMod;
import com.keira.observability.RequestContext;
import com.keira.util.MainThread;
import com.keira.util.Messages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CommandExecutionTool - Allows AI to execute Minecraft commands as a fallback capability.
 * <p>
 * Security features:
 * - Command validation with allowlist/blocklist
 * - Permission checking (admin-only for dangerous commands)
 * - Input sanitization
 * - Audit logging
 * <p>
 * All comments, logs, and tool descriptions are in English.
 * Thread-safe operations via MainThread for server interactions.
 */
public class CommandExecutionTool {

    private final MinecraftServer server;

    // Commands that require admin privileges
    private static final Set<String> ADMIN_ONLY_COMMANDS = new HashSet<>(Arrays.asList(
        "ban", "ban-ip", "banlist", "pardon", "pardon-ip", "op", "deop", "stop", "restart",
        "kick", "whitelist", "save-all", "save-off", "save-on", "reload", "debug"
    ));

    // Commands that are completely blocked for safety
    private static final Set<String> BLOCKED_COMMANDS = new HashSet<>(Arrays.asList(
        "execute", "function", "datapack", "scoreboard"
    ));

    // Commands that are explicitly allowed for regular users
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
        "give", "tp", "teleport", "summon", "fill", "setblock", "clone", "time", "weather",
        "gamemode", "effect", "enchant", "xp", "experience", "clear", "replaceitem",
        "particle", "playsound", "title", "tellraw", "msg", "tell", "say", "me",
        "locate", "locatebiome", "seed", "spawnpoint", "setworldspawn", "gamerule",
        "difficulty", "defaultgamemode", "forceload", "spreadplayers"
    ));

    public CommandExecutionTool(MinecraftServer server) {
        this.server = server;
    }

    @Tool(
        name = "execute_minecraft_command",
        description = """
        Execute a Minecraft command when existing tools cannot fulfill player requests.
        This is a fallback capability for commands not covered by specialized tools.
        
        SECURITY & PERMISSIONS:
        - Regular users can execute safe commands like /give, /tp, /summon, /fill, etc.
        - Admin commands (ban, kick, op, etc.) require admin privileges
        - Some dangerous commands are completely blocked
        - All command execution is logged for security auditing
        
        USAGE EXAMPLES:
        - execute_minecraft_command("player123", "/give @s diamond 64", "Give player diamonds")
        - execute_minecraft_command("admin456", "/summon horse ~ ~ ~", "Summon horse for player")
        - execute_minecraft_command("builder789", "/fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 stone", "Create stone platform")
        
        NOTES:
        - Validate player permissions before calling
        - Use this only when specialized tools don't exist
        - Always provide clear reasoning for the command
        """
    )
    public String executeMinecraftCommand(
        @ToolParam(description = "Player name who requested the command") String playerName,
        @ToolParam(description = "Minecraft command to execute (with leading /)") String command,
        @ToolParam(description = "Reason for executing this command") String reason
    ) {
        String requestId = RequestContext.midTag();
        KeiraAiMod.LOGGER.info("{} [tool:execute_minecraft_command] player='{}' command='{}' reason='{}'", 
            requestId, playerName, command, reason);

        // Validate inputs
        if (playerName == null || playerName.trim().isEmpty()) {
            return "❌ Player name is required";
        }
        
        if (command == null || command.trim().isEmpty()) {
            return "❌ Command is required";
        }

        if (reason == null || reason.trim().isEmpty()) {
            return "❌ Reason is required for command execution";
        }

        // Get player
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName.trim());
        if (player == null) {
            return "❌ Player not found: " + playerName;
        }

        // Normalize command (remove leading slash, convert to lowercase for validation)
        String normalizedCommand = command.trim();
        if (normalizedCommand.startsWith("/")) {
            normalizedCommand = normalizedCommand.substring(1);
        }
        final String finalNormalizedCommand = normalizedCommand; // Make final for lambda

        // Extract command name (first word)
        String commandName = finalNormalizedCommand.split("\\s+")[0].toLowerCase();

        // Security validation
        if (BLOCKED_COMMANDS.contains(commandName)) {
            KeiraAiMod.LOGGER.warn("{} [security] Blocked command execution: player='{}' command='{}'", 
                requestId, playerName, commandName);
            return "❌ Command '" + commandName + "' is blocked for security reasons";
        }

        // Check if command requires admin privileges
        boolean isAdminCommand = ADMIN_ONLY_COMMANDS.contains(commandName);
        boolean isPlayerAdmin = AdminTools.isPlayerAdmin(server, player);

        if (isAdminCommand && !isPlayerAdmin) {
            KeiraAiMod.LOGGER.warn("{} [security] Non-admin attempted admin command: player='{}' command='{}'", 
                requestId, playerName, commandName);
            return "❌ Command '" + commandName + "' requires admin privileges";
        }

        // Check if command is in allowed list (for non-admin commands)
        if (!isAdminCommand && !isPlayerAdmin && !ALLOWED_COMMANDS.contains(commandName)) {
            KeiraAiMod.LOGGER.warn("{} [security] Unknown command attempted: player='{}' command='{}'", 
                requestId, playerName, commandName);
            return "❌ Command '" + commandName + "' is not in the allowed list. Please use specialized tools or contact an admin.";
        }

        // Execute command on main thread
        AtomicReference<String> result = new AtomicReference<>("❌ Command execution failed");
        
        try {
            MainThread.runSync(server, () -> {
                try {
                    // Create command source with appropriate permissions
                    ServerCommandSource source = player.getCommandSource()
                        .withLevel(isPlayerAdmin ? 4 : 2); // Level 4 for admins, level 2 for regular users

                    // Execute the command
                    String fullCommand = finalNormalizedCommand;
                    server.getCommandManager().executeWithPrefix(source, fullCommand);
                    
                    result.set("✅ Command executed successfully: /" + fullCommand);
                    KeiraAiMod.LOGGER.info("{} [success] Command executed: player='{}' command='{}'", 
                        requestId, playerName, fullCommand);
                } catch (Exception e) {
                    String errorMsg = "❌ Command execution failed: " + e.getMessage();
                    result.set(errorMsg);
                    KeiraAiMod.LOGGER.error("{} [error] Command execution failed: player='{}' command='{}' error='{}'", 
                        requestId, playerName, finalNormalizedCommand, e.getMessage());
                }
            });

            // Send feedback to player about command execution
            String feedbackMsg = String.format("🤖 Keira executed: /%s (Reason: %s)", finalNormalizedCommand, reason);
            MainThread.runSync(server, () -> 
                Messages.to(player, Text.translatable("keira.ai.reply", feedbackMsg))
            );

        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("{} [critical] Command execution wrapper failed: player='{}' command='{}'", 
                requestId, playerName, finalNormalizedCommand, e);
            return "❌ Critical error during command execution: " + e.getMessage();
        }

        return result.get();
    }

    /**
     * Get information about command execution capabilities and restrictions.
     */
    @Tool(
        name = "get_command_capabilities",
        description = "Get information about which Minecraft commands can be executed and any restrictions."
    )
    public String getCommandCapabilities(@ToolParam(description = "Player name to check capabilities for") String playerName) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
        if (player == null) {
            return "❌ Player not found: " + playerName;
        }

        boolean isAdmin = AdminTools.isPlayerAdmin(server, player);
        
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Command Execution Capabilities for ").append(playerName).append("\n\n");
        
        sb.append("🛡️ Permission Level: ").append(isAdmin ? "Admin" : "Regular User").append("\n\n");
        
        sb.append("✅ Allowed Commands:\n");
        ALLOWED_COMMANDS.forEach(cmd -> sb.append("  - /").append(cmd).append("\n"));
        
        if (isAdmin) {
            sb.append("\n🔑 Admin Commands (requires admin privileges):\n");
            ADMIN_ONLY_COMMANDS.forEach(cmd -> sb.append("  - /").append(cmd).append("\n"));
        }
        
        sb.append("\n🚫 Blocked Commands (for security):\n");
        BLOCKED_COMMANDS.forEach(cmd -> sb.append("  - /").append(cmd).append("\n"));
        
        sb.append("\n💡 Note: Use specialized tools when available. This is a fallback for commands not covered by other tools.");
        
        return sb.toString();
    }
}