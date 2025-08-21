package com.keira.command.ai;

import com.keira.command.core.Permissions;
import com.keira.ai.ModAdminSystem;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.keira.util.Messages;

public final class HelpCommands {
    private HelpCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("help")
            .executes(HelpCommands::executeHelp);
    }

    public static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        sendHelp(player);
        return 1;
    }

    public static void sendHelp(ServerPlayerEntity player) {
        ModAdminSystem.PermissionLevel perm = Permissions.getPlayerPermission(player);

        // Main command help (internationalization)
        Messages.to(player, Text.translatable("aim.help.header"));
        Messages.to(player, Text.translatable("aim.help.cmd.help"));
        Messages.to(player, Text.translatable("aim.help.cmd.status"));
        Messages.to(player, Text.translatable("aim.help.cmd.chat"));
        Messages.to(player, Text.translatable("aim.help.cmd.say"));
        Messages.to(player, Text.translatable("aim.help.cmd.exit"));
        Messages.to(player, Text.translatable("aim.help.cmd.new"));
        Messages.to(player, Text.translatable("aim.help.footer"));

        Messages.to(player, Text.translatable("aim.help.features.header"));
        Messages.to(player, Text.translatable("aim.help.feature.items"));
        Messages.to(player, Text.translatable("aim.help.feature.tp"));
        Messages.to(player, Text.translatable("aim.help.feature.memory"));
        Messages.to(player, Text.translatable("aim.help.feature.build"));
        Messages.to(player, Text.translatable("aim.help.feature.env"));
        Messages.to(player, Text.translatable("aim.help.feature.player"));
        Messages.to(player, Text.translatable("aim.help.feature.analysis"));
        Messages.to(player, Text.translatable("aim.help.feature.dialog"));

        if (perm.hasPermission(ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.help.admin.header"));
            Messages.to(player, Text.translatable("aim.help.admin.auto.toggle"));
            Messages.to(player, Text.translatable("aim.help.admin.auto.status"));
            Messages.to(player, Text.translatable("aim.help.admin.auto.personal"));
        }

        Messages.to(player, Text.translatable("aim.help.tips.line1"));
        Messages.to(player, Text.translatable("aim.help.tips.line2"));
    }

}
