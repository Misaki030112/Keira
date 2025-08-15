package com.hinadt.chat;

import com.hinadt.AusukaAiMod;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

/**
 * AI Chat System
 * - Owns only the chat listener that routes messages when a player is in AI chat mode
 * - Command tree and handlers live under com.hinadt.command
 */
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
            if (com.hinadt.command.core.AiServices.sessions().isInChat(playerName)) {
                handleAiChatMessage(sender, messageContent);
            }
        });
    }

    private static void handleAiChatMessage(ServerPlayerEntity player, String message) {
        // Process AI response async, then reply on main thread
        CompletableFuture.runAsync(() -> {
            try {
                String response = com.hinadt.command.core.AiServices.workflow().processPlayerMessage(player, message);
                if (response != null && !response.isEmpty()) {
                    com.hinadt.command.core.AiServices.server().execute(() ->
                        player.sendMessage(Text.of("§b[Ausuka.ai] §f" + response))
                    );
                }
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("处理AI聊天消息时出错: " + e.getMessage(), e);
                com.hinadt.command.core.AiServices.server().execute(() ->
                    player.sendMessage(Text.of("§c[Ausuka.ai] 抱歉，我遇到了一些问题 😅 请稍后再试"))
                );
            }
        });
    }

    // Public status helpers used by other systems
    public static boolean isInAiChatMode(String playerName) {
        return com.hinadt.command.core.AiServices.sessions().isInChat(playerName);
    }
}
