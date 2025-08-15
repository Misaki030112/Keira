package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Simple configuration loader for AI credentials and options.
 *
 * Resolution order for values:
 * 1) Java system properties (-DKEY=value)
 * 2) Environment variables (KEY)
 * 3) Config file at <.minecraft>/config/ausuka-ai-mod.properties
 */
public final class AiConfig {
    private static final String CONFIG_FILE_NAME = "ausuka-ai-mod.properties";
    private static Properties props;

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
                    AusukaAiMod.LOGGER.info("读取配置文件: {}", cfg.toAbsolutePath());
                }
            } else {
                AusukaAiMod.LOGGER.info("未找到配置文件，将使用系统属性和环境变量: {}", cfg.toAbsolutePath());
            }
        } catch (IOException e) {
            AusukaAiMod.LOGGER.warn("读取AI配置文件失败，将仅使用系统属性和环境变量", e);
        } catch (Throwable t) {
            // FabricLoader may be unavailable in some contexts; be resilient
            AusukaAiMod.LOGGER.warn("获取配置目录失败，将仅使用系统属性和环境变量: {}", t.toString());
        }
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
}

