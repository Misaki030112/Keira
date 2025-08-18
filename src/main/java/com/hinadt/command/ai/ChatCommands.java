package com.hinadt.command.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.ai.ModAdminSystem;
import com.hinadt.command.core.AiServices;
import com.hinadt.command.core.Permissions;
import com.hinadt.util.Messages;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import java.util.UUID;
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
            Messages.to(player, Text.translatable("aim.not_ready"));
            return 0;
        }
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        String name = player.getName().getString();
        if (isAiAssistantName(name)) {
            Messages.to(player, Text.translatable("aim.name.forbidden"));
            return 0;
        }
        if (AiServices.sessions().isInChat(name)) {
            Messages.to(player, Text.translatable("aim.already_in_chat"));
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

    /**
     * One-shot ask: Sends a single message to the AI without using or updating
     * any conversation session/history. Replies are localized to the player's client language.
     */
    @SuppressWarnings("resource")
    public static int sayOnce(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!AiRuntime.isReady()) {
            Messages.to(player, Text.translatable("aim.not_ready"));
            return 0;
        }
        if (!Permissions.has(player, ModAdminSystem.PermissionLevel.USER)) {
            Messages.to(player, Text.translatable("aim.no_permission"));
            return 0;
        }
        String msg = StringArgumentType.getString(ctx, "message");
        String messageId = UUID.randomUUID().toString();
        CompletableFuture
            .supplyAsync(() -> AiServices.workflow().processSingleTurnMessage(player, msg, messageId),
                    AiRuntime.AI_EXECUTOR)
            .orTimeout(60, TimeUnit.SECONDS)
            .whenComplete((response, ex) -> {
                if (ex != null) {
                    AusukaAiMod.LOGGER.warn("[mid={}] /ai say timed out or failed: {}", messageId, ex.toString());
                    AiServices.server().execute(() -> Messages.to(player, Text.translatable("aim.say.error")));
                    return;
                }
                String out = (response == null || response.isEmpty())
                        ? Text.translatable("aim.say.no_response").getString()
                        : response;
                String preview = out.substring(0, Math.min(180, out.length())).replaceAll("\n", " ");
                AusukaAiMod.LOGGER.debug("[mid={}] [/ai say] Sending AI reply to player={}, len={}, preview='{}'",
                        messageId, player.getName().getString(), out.length(), preview);
                AiServices.server().execute(() -> Messages.to(player, Text.translatable("ausuka.ai.reply", out)));
            });
        return 1;
    }
}
