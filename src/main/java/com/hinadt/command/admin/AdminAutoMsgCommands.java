package com.hinadt.command.admin;

import com.hinadt.command.core.AiServices;
import com.hinadt.command.core.Permissions;
import com.hinadt.ai.ModAdminSystem;
import com.hinadt.chat.IntelligentAutoMessageSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.hinadt.util.Messages;
import com.hinadt.persistence.MyBatisSupport;
import com.hinadt.persistence.mapper.ConversationMapper;
import com.hinadt.AusukaAiMod;

public final class AdminAutoMsgCommands {
    private AdminAutoMsgCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("admin")
            .executes(AdminAutoMsgCommands::help)
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("auto-msg")
                .executes(AdminAutoMsgCommands::autoMsgHelp)
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("toggle")
                    .executes(AdminAutoMsgCommands::toggle))
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("status")
                    .executes(AdminAutoMsgCommands::status))
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("personal")
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.word())
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("on").executes(ctx -> personal(ctx, true)))
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("off").executes(ctx -> personal(ctx, false)))))
                )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("stats")
                .executes(AdminAutoMsgCommands::statsSummary)
            );
    }

    public static int help(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        Messages.to(player, Text.translatable("aim.admin.title"));
        Messages.to(player, Text.translatable("aim.admin.auto.toggle"));
        Messages.to(player, Text.translatable("aim.admin.auto.status"));
        Messages.to(player, Text.translatable("aim.admin.auto.personal"));
        return 1;
    }

    public static int autoMsgHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        Messages.to(player, Text.translatable("aim.admin.auto.title"));
        Messages.to(player, Text.translatable("aim.admin.auto.toggle"));
        Messages.to(player, Text.translatable("aim.admin.auto.status"));
        Messages.to(player, Text.translatable("aim.admin.auto.personal"));
        return 1;
    }

    public static int toggle(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        boolean newState = !IntelligentAutoMessageSystem.isSystemEnabled();
        IntelligentAutoMessageSystem.toggleAutoMessages(newState);
        Messages.to(player, Text.translatable(newState ? "aim.auto.enabled" : "aim.auto.disabled"));
        return 1;
    }

    @SuppressWarnings("resource")
    public static int status(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        boolean enabled = IntelligentAutoMessageSystem.isSystemEnabled();
        int playerCount = AiServices.server().getPlayerManager().getPlayerList().size();
        Messages.to(player, Text.translatable("aim.auto.status", enabled ? Text.translatable("aim.enabled") : Text.translatable("aim.disabled")));
        Messages.to(player, Text.translatable("aim.auto.online", playerCount));
        return 1;
    }

    private static int personal(CommandContext<ServerCommandSource> ctx, boolean enable) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "player");
        IntelligentAutoMessageSystem.togglePlayerAutoMessages(target, enable);
        Messages.to(player, Text.translatable(enable ? "aim.auto.personal.enabled" : "aim.auto.personal.disabled", target));
        return 1;
    }

    public static int statsSummary(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }

        try (var session = MyBatisSupport.getFactory().openSession()) {
            var conv = session.getMapper(ConversationMapper.class).getStats();
            int totalMessages = ((Number) conv.getOrDefault("total_messages", 0)).intValue();
            int uniquePlayers = ((Number) conv.getOrDefault("unique_players", 0)).intValue();
            int totalSessions = ((Number) conv.getOrDefault("total_sessions", 0)).intValue();
            int inChat = AiServices.sessions().count();

            Messages.to(player, Text.translatable("aim.stats.title"));
            Messages.to(player, Text.translatable("aim.stats.messages", totalMessages));
            Messages.to(player, Text.translatable("aim.stats.players", uniquePlayers));
            Messages.to(player, Text.translatable("aim.stats.sessions", totalSessions));
            Messages.to(player, Text.translatable("aim.stats.inchat", inChat));
            Messages.to(player, Text.translatable("aim.stats.footer"));
            return 1;
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("Statistics query failed", e);
            Messages.to(player, Text.translatable("aim.say.error"));
            return 0;
        }
    }
}
