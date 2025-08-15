package com.hinadt.command.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.command.core.AiServices;
import com.hinadt.command.core.Permissions;
import com.hinadt.ai.ModAdminSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ChatCommands {
    private ChatCommands() {}

    public static LiteralArgumentBuilder<ServerCommandSource> chat() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("chat")
            .executes(ChatCommands::enterChat);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> exit() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("exit")
            .executes(ChatCommands::exitChat);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> status() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("status")
            .executes(ChatCommands::statusChat);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> startNew() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("new")
            .executes(ChatCommands::newSession);
    }

    public static LiteralArgumentBuilder<ServerCommandSource> say() {
        return LiteralArgumentBuilder.<ServerCommandSource>literal("say")
            .then(net.minecraft.server.command.CommandManager.argument("message", StringArgumentType.greedyString())
                .executes(ChatCommands::sayOnce));
    }

    private static boolean isAiAssistantName(String name) {
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        String[] forbidden = {"ausukaai", "ausuka", "aiausuka", "misaki", "aimisaki"};
        for (String f : forbidden) if (normalized.contains(f)) return true;
        return false;
    }

    public static int enterChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 权限不足"));
            return 0;
        }
        String name = player.getName().getString();
        if (isAiAssistantName(name)) {
            player.sendMessage(Text.of("§c[系统] 检测到AI助手身份，禁止进入AI聊天模式"));
            return 0;
        }
        if (AiServices.sessions().isInChat(name)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 你已经在AI聊天模式中了！使用 /ai exit 退出"));
            return 0;
        }
        AiServices.sessions().enter(name);
        player.sendMessage(net.minecraft.text.Text.translatable("aim.enter.1"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.enter.2"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.enter.3"));
        ChatHelpers.sendAiWelcomeMessage(player);
        return 1;
    }

    public static int exitChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String name = player.getName().getString();
        if (!AiServices.sessions().isInChat(name)) {
            player.sendMessage(net.minecraft.text.Text.translatable("aim.not_in_chat"));
            return 0;
        }
        AiServices.sessions().exit(name);
        player.sendMessage(net.minecraft.text.Text.translatable("aim.exit"));
        return 1;
    }

    public static int newSession(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String id = AiRuntime.getConversationMemory().startNewConversation(player.getName().getString());
        player.sendMessage(net.minecraft.text.Text.translatable("aim.new.started"));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.new.id", id));
        player.sendMessage(net.minecraft.text.Text.translatable("aim.new.info"));
        return 1;
    }

    public static int statusChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        boolean in = AiServices.sessions().isInChat(player.getName().getString());
        player.sendMessage(net.minecraft.text.Text.translatable(in ? "aim.status.in" : "aim.status.out"));
        return 1;
    }

    public static int sayOnce(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            player.sendMessage(Text.of("§c[Ausuka.ai] 权限不足"));
            return 0;
        }
        String msg = StringArgumentType.getString(ctx, "message");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String response = AiServices.workflow().processPlayerMessage(player, msg);
                if (response != null && !response.isEmpty()) {
                    AiServices.server().execute(() -> player.sendMessage(Text.of("§b[Ausuka.ai] §f" + response)));
                }
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("处理 /ai say 时出错", e);
                AiServices.server().execute(() -> player.sendMessage(net.minecraft.text.Text.translatable("aim.say.error")));
            }
        });
        return 1;
    }
}
