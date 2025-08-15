package com.hinadt.command;

import com.hinadt.command.admin.AdminAutoMsgCommands;
import com.hinadt.command.ai.ChatCommands;
import com.hinadt.command.ai.HelpCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

/**
 * Centralized registration for /ai commands.
 * Keeps Brigadier tree construction under a dedicated command package.
 */
public class AiCommandRegistry {

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = CommandManager.literal("ai")
                .executes(HelpCommands::executeHelp)
                .then(HelpCommands.build())
                .then(ChatCommands.chat())
                .then(ChatCommands.say())
                .then(ChatCommands.exit())
                .then(ChatCommands.startNew())
                .then(ChatCommands.status())
                .then(AdminAutoMsgCommands.build());
            dispatcher.register(root);
        });
    }
}
