package com.hinadt.chat;

import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.command.core.AiServices;
import com.hinadt.util.Async;
import com.hinadt.util.Messages;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * AI Chat System
 * - Owns only the chat listener that routes messages when a player is in AI chat mode
 * - Command tree and handlers live under com.hinadt.command
 */
@SuppressWarnings("resource")
public class AiChatSystem {

    public static void initialize() {
        // Service wiring is done in AiServices; here we only attach listeners.
        registerChatListener();
        AusukaAiMod.LOGGER.info("AI chat system initialized.");
    }

    private static void registerChatListener() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerName = sender.getName().getString();
            String messageContent = message.getContent().getString();

            // Route chat to AI when player is in AI chat mode
            if (AiServices.sessions().isInChat(playerName)) {
                handleAiChatMessage(sender, messageContent);
            }
        });
    }

    private static void handleAiChatMessage(ServerPlayerEntity player, String message) {
        String playerName = player.getName().getString();
        String messageId = UUID.randomUUID().toString();
        AusukaAiMod.LOGGER.debug("[mid={}] [chat] Received player message: player={}, msg='{}'", messageId, playerName, message);

        // Run on dedicated pool and enforce a hard timeout by cancelling the task (interruptible)
        CompletableFuture<String> task = Async.supplyAsyncWithTimeout(
                () -> AiServices.workflow().processPlayerMessage(player, message, messageId),
                AiRuntime.AI_EXECUTOR,
                120, TimeUnit.SECONDS
        );

        task.whenComplete((response, ex) -> {

            if (ex != null) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                        ? ex.getCause() : ex;

                if (cause instanceof CancellationException) {
                    // Timeout path
                    AusukaAiMod.LOGGER.warn("[mid={}] [chat] AI task timed out and was cancelled: player={}", messageId, playerName);
                    AiServices.server().execute(() -> {
                        MutableText t = Text.translatable("ausuka.ai.timeout");
                        Messages.to(player, Text.of("§c").copy().append(t));
                    });
                } else {
                    // Other failure
                    AusukaAiMod.LOGGER.warn("[mid={}] [chat] Message processing failed: player={}, err={}", messageId, playerName, cause.toString());
                    AiServices.server().execute(() -> {
                        // Reuse existing i18n message for generic failure if present; fallback to timeout msg style
                        MutableText t = Text.translatable("ausuka.ai.timeout");
                        Messages.to(player, Text.of("§c").copy().append(t));
                    });
                }
                return;
            }

            String out = (response == null || response.isEmpty())
                    ? Text.translatable("ausuka.ai.no_response").getString()
                    : response;
            AiServices.server().execute(() ->
                    Messages.to(player, Text.translatable("ausuka.ai.reply", out))
            );
        });
    }

    // Public status helpers used by other systems
    public static boolean isInAiChatMode(String playerName) {
        return AiServices.sessions().isInChat(playerName);
    }
}
