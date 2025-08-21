package com.keira.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Supplier;

/**
 * Unified message helpers to avoid unsigned chat getting hidden by clients.
 * Prefer system/overlay messages and command feedback channels.
 */
public final class Messages {
    private Messages() {}

    /**
     * Send a system-style message to a single player.
     * Uses overlay=false to show in chat area without signature requirements.
     */
    public static void to(ServerPlayerEntity player, Text text) {
        try {
            player.sendMessageToClient(text, false);
        } catch (Throwable t) {
            player.sendMessage(text);
        }
    }

    /**
     * Send an overlay (actionbar) style system message.
     */
    public static void overlay(ServerPlayerEntity player, Text text) {
        try {
            player.sendMessageToClient(text, true);
        } catch (Throwable t) {
            player.sendMessage(text);
        }
    }

    /**
     * Send command feedback to the command source (system channel).
     */
    public static void feedback(ServerCommandSource source, Text text) {
        Supplier<Text> supplier = () -> text;
        source.sendFeedback(supplier, false);
    }

    /**
     * Broadcast a system message to all players.
     */
    public static void broadcast(MinecraftServer server, Text text) {
        try {
            server.getPlayerManager().broadcast(text, true);
        } catch (Throwable t) {
            // no-op fallback
        }
    }
}

