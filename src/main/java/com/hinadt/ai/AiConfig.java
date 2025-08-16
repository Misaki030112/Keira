package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Simple configuration loader for AI credentials and options.
 *
 * Resolution order (high → low):
 * 1) Java system properties (-DKEY=value)
 * 2) Environment variables (KEY)
 * 3) Config file at <.minecraft>/config/ausuka-ai-mod.properties
 * 4) Minecraft server.properties (lowest priority, read via server APIs when possible)
 */
public final class AiConfig {
    private static final String CONFIG_FILE_NAME = "ausuka-ai-mod.properties";
    private static Properties props;
    private static volatile MinecraftServer attachedServer;

    private AiConfig() {}

    public static synchronized void load() {
        if (props != null) return;
        props = new Properties();
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            Path cfg = configDir.resolve(CONFIG_FILE_NAME);
            if (Files.exists(cfg)) {
                try (InputStream in = Files.newInputStream(cfg)) {
                    props.load(in);
                    AusukaAiMod.LOGGER.info("Loaded AI config file: {}", cfg.toAbsolutePath());
                }
            } else {
                AusukaAiMod.LOGGER.info("AI config file not found; will use system properties and environment variables: {}", cfg.toAbsolutePath());
            }
        } catch (IOException e) {
            AusukaAiMod.LOGGER.warn("Failed to read AI config file; falling back to system properties and environment variables", e);
        } catch (Throwable t) {
            // FabricLoader may be unavailable in some contexts; be resilient
            AusukaAiMod.LOGGER.warn("Failed to obtain config directory; falling back to system properties and environment variables: {}", t.toString());
        }
    }

    /**
     * Attach the running server so we can consult server.properties via Minecraft APIs.
     * This is optional; when absent, server.properties resolution is skipped.
     */
    public static void attachServer(MinecraftServer server) {
        attachedServer = server;
    }

    /**
     * Get value by key using precedence: system property -> env -> file.
     */
    public static String get(String key) {
        // System property
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        // Environment variable (accept both exact and upper-case variants)
        v = System.getenv(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key.toUpperCase(Locale.ROOT));
        if (v != null && !v.isEmpty()) return v;
        // Config file
        load();
        if (props == null) return null;
        v = props.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = props.getProperty(key.toUpperCase(Locale.ROOT));
        if (v != null && !v.isEmpty()) return v;

        // server.properties (lowest priority)
        v = fromServerProperties(key);
        if (v != null && !v.isEmpty()) return v;
        v = fromServerProperties(key.toUpperCase(Locale.ROOT));
        return (v == null || v.isEmpty()) ? null : v;
    }

    /**
     * Helper to determine desired provider from config (optional).
     */
    public static String getPreferredProvider() {
        String v = get("AI_PROVIDER");
        if (v == null || v.isBlank()) return null;
        return v.trim().toLowerCase(Locale.ROOT);
    }

    // --- internals ---

    /**
     * Resolve a value from server.properties using Minecraft's own property holder if available.
     * Avoids manual parsing by reflectively accessing dedicated server properties.
     */
    private static String fromServerProperties(String key) {
        MinecraftServer srv = attachedServer;
        if (srv == null) return null;
        try {
            // Dedicated server typically exposes a getProperties() returning DedicatedServerProperties
            Object dedicatedProps = tryInvokeNoArg(srv, "getProperties");
            if (dedicatedProps != null) {
                // Try a field named 'properties' of type java.util.Properties
                Properties p = tryGetPropertiesField(dedicatedProps);
                if (p != null) return p.getProperty(key);
                // Or a no-arg method returning Properties
                Object maybeProps = tryInvokeNoArg(dedicatedProps, "getProperties");
                if (maybeProps instanceof Properties props) return props.getProperty(key);
                // Some versions might keep a raw map
                Object raw = tryInvokeNoArg(dedicatedProps, "rawProperties");
                if (raw instanceof Properties props2) return props2.getProperty(key);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object tryInvokeNoArg(Object target, String method) {
        try {
            var m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Properties tryGetPropertiesField(Object holder) {
        try {
            var f = holder.getClass().getDeclaredField("properties");
            f.setAccessible(true);
            Object v = f.get(holder);
            return (v instanceof Properties p) ? p : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
