package com.hinadt.chat;

import com.hinadt.AusukaAiMod;
import com.hinadt.command.core.AiServices;
import com.hinadt.ai.AiRuntime;
import com.hinadt.tools.Messages;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
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
        AusukaAiMod.LOGGER.info("AI聊天系统初始化完成！");
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
        AusukaAiMod.LOGGER.debug("[AI] 收到聊天消息: player={}, msg='{}'", playerName, message);

        // Process on dedicated pool with a guard timeout, switch back to main thread to reply
        CompletableFuture
            .supplyAsync(() -> AiServices.workflow().processPlayerMessage(player, message),
                    AiRuntime.AI_EXECUTOR)
            .orTimeout(60, TimeUnit.SECONDS)
            .whenComplete((response, ex) -> {
                if (ex != null) {
                    AusukaAiMod.LOGGER.warn("[AI] 处理消息失败/超时: player={}, err={}", playerName, ex.toString());
                    AiServices.server().execute(() ->
                            Messages.to(player, Text.of("§c[Ausuka.ai] 响应超时或出错了，请稍后再试。"))
                    );
                    return;
                }

                String out = (response == null || response.isEmpty())
                        ? "超时，我没能给出回答，请换种说法再试试~"
                        : response;

                AiServices.server().execute(() ->
                        Messages.to(player, Text.of("§b[Ausuka.ai] §f" + out))
                );
            });
    }

    // Public status helpers used by other systems
    public static boolean isInAiChatMode(String playerName) {
        return AiServices.sessions().isInChat(playerName);
    }
}
