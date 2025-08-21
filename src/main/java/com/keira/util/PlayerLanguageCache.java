package com.keira.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory player language cache.
 *
 * Policy:
 * - Capture language when a player joins or updates client options.
 * - Do NOT probe language for every message; always read from this cache.
 * - Server default is en_us.
 */
public final class PlayerLanguageCache {
    private static final String DEFAULT_CODE = PlayerLocales.serverCode();
    private static final Locale DEFAULT_LOCALE = PlayerLocales.serverLocale();
    private static final Map<UUID, String> CACHE = new ConcurrentHashMap<>();

    private PlayerLanguageCache() {}

    /**
     * Update or insert a player's current client language.
     */
    public static void update(ServerPlayerEntity player) {
        if (player == null) return;
        UUID id = player.getUuid();
        String code = PlayerLocales.code(player);
        CACHE.put(id, code);
    }

    /**
     * Remove a player entry from cache (on leave).
     */
    public static void remove(ServerPlayerEntity player) {
        if (player == null) return;
        CACHE.remove(player.getUuid());
    }

    /**
     * Get cached language code, or server default.
     */
    public static String code(ServerPlayerEntity player) {
        if (player == null) return DEFAULT_CODE;
        return CACHE.getOrDefault(player.getUuid(), DEFAULT_CODE);
    }

    /**
     * Locale for player or server default.
     */
    public static Locale locale(ServerPlayerEntity player) {
        return PlayerLocales.toLocale(code(player));
    }

    /**
     * Locale for a command source (players use cached locale; console uses server default).
     */
    public static Locale locale(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return player != null ? locale(player) : DEFAULT_LOCALE;
    }
}

