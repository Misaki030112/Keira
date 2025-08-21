package com.keira.ai;

import com.keira.KeiraAiMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.AbstractPropertiesHandler;

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
 * 3) Config file at <.minecraft>/config/keira.properties
 * 4) Minecraft server.properties (lowest priority, read via server APIs when possible)
 */
public final class AiConfig {
    private static final String CONFIG_FILE_NAME = "keira.properties";
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
                    KeiraAiMod.LOGGER.info("Loaded AI config file: {}", cfg.toAbsolutePath());
                }
            } else {
                KeiraAiMod.LOGGER.info("AI config file not found; will use system properties and environment variables: {}", cfg.toAbsolutePath());
            }
        } catch (IOException e) {
            KeiraAiMod.LOGGER.warn("Failed to read AI config file; falling back to system properties and environment variables", e);
        } catch (Throwable t) {
            // FabricLoader may be unavailable in some contexts; be resilient
            KeiraAiMod.LOGGER.warn("Failed to obtain config directory; falling back to system properties and environment variables: {}", t.toString());
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
        v = System.getenv(key.toLowerCase(Locale.ROOT));
        if (v != null && !v.isEmpty()) return v;
        // Config file
        load();
        if (props == null) return null;
        v = props.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = props.getProperty(key.toLowerCase(Locale.ROOT));
        if (v != null && !v.isEmpty()) return v;

        // server.properties (lowest priority)
        v = fromServerProperties(key);
        if (v != null && !v.isEmpty()) return v;
        v = fromServerProperties(key.toLowerCase(Locale.ROOT));
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
     * Resolve a value from server.properties using the official API for the current version (Yarn 1.21.8).
     * We do not use reflection; instead we load the file through AbstractPropertiesHandler to ensure parity.
     */
    private static String fromServerProperties(String key) {
        MinecraftServer srv = attachedServer;
        if (srv == null) return null;
        // Only dedicated servers maintain a server.properties file
        if (!srv.isDedicated()) return null;
        try {
            Path runDirectory = srv.getRunDirectory();
            Properties properties = AbstractPropertiesHandler.loadProperties(runDirectory.resolve("server.properties"));
            return properties.getProperty(key);
        } catch (Throwable t) {
            // Use debug level to avoid noisy logs; this is a best-effort fallback
            KeiraAiMod.LOGGER.debug("Failed to read server.properties for key '{}': {}", key, t.toString());
            return null;
        }
    }
}

