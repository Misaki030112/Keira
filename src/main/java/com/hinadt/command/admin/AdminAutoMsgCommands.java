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
            player.sendMessage(Text.of("§c[Ausuka.ai] 只有管理员才能访问管理功能"));
            return 0;
        }
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.title"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.toggle"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.status"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.personal"));
        return 1;
    }

    public static int autoMsgHelp(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 只有管理员才能控制自动消息系统"));
            return 0;
        }
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.title"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.toggle"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.status"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.admin.auto.personal"));
        return 1;
    }

    public static int toggle(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 只有管理员才能控制自动消息系统"));
            return 0;
        }
        boolean newState = !IntelligentAutoMessageSystem.isSystemEnabled();
        String result = IntelligentAutoMessageSystem.toggleAutoMessages(newState);
        player.sendMessage(net.minecraft.text.Text.translatable("aim.prefix_text", result));
        return 1;
    }

    public static int status(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 只有管理员才能查看自动消息系统状态"));
            return 0;
        }
        boolean enabled = IntelligentAutoMessageSystem.isSystemEnabled();
        int playerCount = AiServices.server().getPlayerManager().getPlayerList().size();
        player.sendMessage(net.minecraft.text.Text.translatable("aim.auto.status", enabled ? net.minecraft.text.Text.translatable("aim.enabled") : net.minecraft.text.Text.translatable("aim.disabled")));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.auto.online", playerCount));
        return 1;
    }

    private static int personal(CommandContext<ServerCommandSource> ctx, boolean enable) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 只有管理员才能控制自动消息系统"));
            return 0;
        }
        String target = StringArgumentType.getString(ctx, "player");
        String result = IntelligentAutoMessageSystem.togglePlayerAutoMessages(target, enable);
        player.sendMessage(net.minecraft.text.Text.translatable("aim.prefix_text", result));
        return 1;
    }

    public static int statsSummary(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.MOD_ADMIN)) {
            player.sendMessage(net.minecraft.text.Text.translatable("aim.no_permission"));
            return 0;
        }

        try (var session = com.hinadt.persistence.MyBatisSupport.getFactory().openSession()) {
            var conv = session.getMapper(com.hinadt.persistence.mapper.ConversationMapper.class).getStats();
            int totalMessages = ((Number) conv.getOrDefault("total_messages", 0)).intValue();
            int uniquePlayers = ((Number) conv.getOrDefault("unique_players", 0)).intValue();
            int totalSessions = ((Number) conv.getOrDefault("total_sessions", 0)).intValue();
            int inChat = session.getMapper(com.hinadt.persistence.mapper.ChatSessionMapper.class).countInChat();

            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.title"));
            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.messages", totalMessages));
            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.players", uniquePlayers));
            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.sessions", totalSessions));
            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.inchat", inChat));
            player.sendMessage(net.minecraft.text.Text.translatable("aim.stats.footer"));
            return 1;
        } catch (Exception e) {
            com.hinadt.AusukaAiMod.LOGGER.error("统计信息查询失败", e);
            player.sendMessage(net.minecraft.text.Text.translatable("aim.say.error"));
            return 0;
        }
    }
}
