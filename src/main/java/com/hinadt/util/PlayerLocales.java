package com.hinadt.util;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Player locale utilities (1.21.8-first with graceful fallback).
 *
 * Rationale:
 * - Keep compatibility logic isolated from call sites.
 * - Use Minecraft 1.21.8 API where available.
 * - Default server-side language is en_us.
 */
public final class PlayerLocales {
    private static final String DEFAULT_CODE = "en_us";
    private static final Locale DEFAULT_LOCALE = Locale.US;

    private PlayerLocales() {}

    /**
     * Returns the player's client language code (e.g., "en_us").
     * Falls back to "en_us" if unavailable.
     */
    public static String code(ServerPlayerEntity player) {
        if (player == null) return DEFAULT_CODE;
        try {
            Object opts = player.getClientOptions();
            if (opts == null) return DEFAULT_CODE;

            // 1.21.8 (Yarn) exposes a record accessor: language()
            String lang = invokeLanguageAccessor(opts);
            if (lang == null || lang.isBlank()) return DEFAULT_CODE;
            return normalize(lang);
        } catch (Throwable ignored) {
            return DEFAULT_CODE;
        }
    }

    /**
     * Returns the player's preferred Locale object.
     */
    public static Locale locale(ServerPlayerEntity player) {
        return toLocale(code(player));
    }

    /**
     * Returns a reasonable Locale for a command source (player or console).
     * Console and non-player sources use en_us.
     */
    public static Locale locale(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return player != null ? locale(player) : DEFAULT_LOCALE;
    }

    /**
     * Server-default language code.
     */
    public static String serverCode() {
        return DEFAULT_CODE;
    }

    /**
     * Server-default Locale (en_US).
     */
    public static Locale serverLocale() {
        return DEFAULT_LOCALE;
    }

    /**
     * Convert a Minecraft-style code like "en_us" or a BCP47-like code
     * like "en-US" into a Java Locale. Unknowns map to en_US.
     */
    public static Locale toLocale(String code) {
        if (code == null || code.isBlank()) return DEFAULT_LOCALE;
        String norm = normalize(code);
        // Convert to BCP-47 tag for standard parsing (e.g., en_us -> en-US)
        String bcp47 = norm.replace('_', '-');
        int idx = bcp47.indexOf('-');
        if (idx > 0 && idx < bcp47.length() - 1) {
            String lang = bcp47.substring(0, idx);
            String region = bcp47.substring(idx + 1).toUpperCase(Locale.ROOT);
            bcp47 = lang + "-" + region;
        }
        Locale l = Locale.forLanguageTag(bcp47);
        return (l == null || l.equals(Locale.ROOT)) ? DEFAULT_LOCALE : l;
    }

    // --- internals ---

    private static String normalize(String code) {
        String c = code.trim();
        c = c.replace('-', '_');
        c = c.toLowerCase(Locale.ROOT);
        // Basic shape validation: ll[_CC]
        if (!c.matches("[a-z]{2,3}(_[a-z]{2})?")) {
            return DEFAULT_CODE;
        }
        return c;
    }

    private static String invokeLanguageAccessor(Object clientOptions) {
        // Prefer 1.21.8 record accessor: language()
        String value = tryInvokeNoArgString(clientOptions, "language");
        if (value != null) return value;

        // Fallback for older mappings: getLanguage()
        return tryInvokeNoArgString(clientOptions, "getLanguage");
    }

    private static String tryInvokeNoArgString(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object v = m.invoke(target);
            return (v instanceof String s) ? s : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
