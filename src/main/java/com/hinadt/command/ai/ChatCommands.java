package com.hinadt.command.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.ai.ModAdminSystem;
import com.hinadt.command.core.AiServices;
import com.hinadt.command.core.Permissions;
import com.hinadt.tools.Messages;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
            .then(argument("message", StringArgumentType.greedyString())
                .executes(ChatCommands::sayOnce));
    }

    private static boolean isAiAssistantName(String name) {
        String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        String[] forbidden = {"ausuka"};
        for (String f : forbidden) if (normalized.contains(f)) return true;
        return false;
    }

    public static int enterChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!AiRuntime.isReady()) {
            Messages.to(player, Text.of("§e[Ausuka.ai] AI未配置或不可用，请先配置 API 密钥。"));
            return 0;
        }
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            Messages.to(player, Text.of("§c[Ausuka.ai] 权限不足"));
            return 0;
        }
        String name = player.getName().getString();
        if (isAiAssistantName(name)) {
            Messages.to(player, Text.of("§c[系统] 检测到AI助手身份，禁止进入AI聊天模式"));
            return 0;
        }
        if (AiServices.sessions().isInChat(name)) {
            Messages.to(player, Text.of("§c[Ausuka.ai] 你已经在AI聊天模式中了！使用 /ai exit 退出"));
            return 0;
        }
        AiServices.sessions().enter(name);
        Messages.to(player, Text.translatable("aim.enter.1"));
        Messages.to(player, Text.translatable("aim.enter.2"));
        Messages.to(player, Text.translatable("aim.enter.3"));
        ChatHelpers.sendAiWelcomeMessage(player);
        return 1;
    }

    public static int exitChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String name = player.getName().getString();
        if (!AiServices.sessions().isInChat(name)) {
            Messages.to(player, Text.translatable("aim.not_in_chat"));
            return 0;
        }
        AiServices.sessions().exit(name);
        Messages.to(player, Text.translatable("aim.exit"));
        return 1;
    }

    public static int newSession(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String id = AiRuntime.getConversationMemory().startNewConversation(player.getName().getString());
        Messages.to(player, Text.translatable("aim.new.started"));
        Messages.to(player, Text.translatable("aim.new.id", id));
        Messages.to(player, Text.translatable("aim.new.info"));
        return 1;
    }

    public static int statusChat(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        boolean in = AiServices.sessions().isInChat(player.getName().getString());
        Messages.to(player, Text.translatable(in ? "aim.status.in" : "aim.status.out"));
        return 1;
    }

    public static int sayOnce(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!AiRuntime.isReady()) {
            Messages.to(player, Text.of("§e[Ausuka.ai] AI未配置或不可用，请先配置 API 密钥。"));
            return 0;
        }
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            Messages.to(player, Text.of("§c[Ausuka.ai] 权限不足"));
            return 0;
        }
        String msg = StringArgumentType.getString(ctx, "message");
        CompletableFuture
            .supplyAsync(() -> AiServices.workflow().processPlayerMessage(player, msg),
                    AiRuntime.AI_EXECUTOR)
            .orTimeout(60, TimeUnit.SECONDS)
            .whenComplete((response, ex) -> {
                if (ex != null) {
                    AusukaAiMod.LOGGER.warn("处理 /ai say 超时或失败: {}", ex.toString());
                    AiServices.server().execute(() -> Messages.to(player, Text.translatable("aim.say.error")));
                    return;
                }
                String out = (response == null || response.isEmpty())
                        ? "我没能给出回答，请换种说法再试试~"
                        : response;
                AiServices.server().execute(() -> Messages.to(player, Text.of("§b[Ausuka.ai] §f" + out)));
            });
        return 1;
    }
}
