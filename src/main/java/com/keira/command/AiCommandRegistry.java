package com.keira.command;

import com.keira.command.admin.AdminAutoMsgCommands;
import com.keira.command.ai.ChatCommands;
import com.keira.command.ai.HelpCommands;
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
