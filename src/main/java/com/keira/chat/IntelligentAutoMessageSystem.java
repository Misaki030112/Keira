package com.keira.chat;

import com.keira.KeiraAiMod;
import com.keira.ai.AiRuntime;
import com.keira.ai.context.PlayerContextBuilder;
import com.keira.util.Messages;
import com.keira.util.PlayerLanguageCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Intelligent Auto Message System (AI-driven).
 *
 * Responsibilities:
 * - Periodically broadcast server-wide tips based on world state.
 * - Periodically send personalized tips to players based on their status.
 *
 * UX:
 * - Broadcasts are shown as non-chat HUD overlays (action bar) to avoid clutter.
 *   True top-right "toast" notifications require a client-side UI implementation;
 *   until then we use overlay/actionbar as a toast-like substitute.
 * - Personalized messages are delivered via localized system messages.
 *
 * Internationalization policy:
 * - Personalized outputs: AI must reply in each player's client language.
 * - Broadcast outputs: AI must reply in the majority language among online players
 *   (fallback en_us when unknown or tied).
 *
 * Admin controls:
 * - toggleSystem(boolean): enable/disable the whole feature set.
 * - toggleBroadcasts(boolean): enable/disable periodic broadcasts only.
 * - togglePersonalized(boolean): enable/disable periodic personalized tips only.
 * - togglePlayerAutoMessages(String, boolean): per-player opt-out of personalized tips.
 *   Usage: call these from your command handlers (see com.hinadt.command). For example,
 *   a command like "/ai-auto broadcast off" can call toggleBroadcasts(false), and
 *   "/ai-auto personal off <player>" can call togglePlayerAutoMessages(name, false).
 */
public class IntelligentAutoMessageSystem {

    private static MinecraftServer server;
    private static ScheduledExecutorService scheduler;

    // global and fine-grained switches
    private static volatile boolean systemEnabled = true;
    private static volatile boolean broadcastsEnabled = true;
    private static volatile boolean personalizedEnabled = true;

    private static final ConcurrentHashMap<String, Boolean> playerOptOut = new ConcurrentHashMap<>();

    // scheduling intervals (minutes)
    private static final int BROADCAST_INTERVAL_MIN = 15;
    private static final int PERSONAL_INTERVAL_MIN = 10;

    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        scheduler = Executors.newScheduledThreadPool(2);

        // periodic AI-driven broadcast
        scheduler.scheduleAtFixedRate(
            IntelligentAutoMessageSystem::sendAiBroadcastMessage,
            BROADCAST_INTERVAL_MIN,
            BROADCAST_INTERVAL_MIN,
            TimeUnit.MINUTES
        );

        // periodic personalized tips
        scheduler.scheduleAtFixedRate(
            IntelligentAutoMessageSystem::sendPersonalizedMessages,
            PERSONAL_INTERVAL_MIN,
            PERSONAL_INTERVAL_MIN,
            TimeUnit.MINUTES
        );

        KeiraAiMod.LOGGER.info("Intelligent auto message system started.");
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    // ---- Broadcasts ----

    private static void sendAiBroadcastMessage() {
        if (!systemEnabled || !broadcastsEnabled || !AiRuntime.isReady() || server.getPlayerManager().getPlayerList().isEmpty()) {
            return;
        }

        try {
            String worldContext = gatherWorldContext();
            String responseLocale = majorityLocaleCode();

            String prompt = String.format("""
You are Keira, an in-server assistant for Minecraft.
Task: Generate a concise, fun, useful server-wide tip based on current world state.

[Output Language]
Must be in: %s (fallback en_us). Do not include translation notes.

[Content Requirements]
- Keep it short (<= 120 characters, including emoji if helpful).
- Make it relevant to the current world status.
- Avoid repetition of previous generic content.
- No Markdown, no code blocks.

[World Status]
%s
""", responseLocale, worldContext);

            long start = System.currentTimeMillis();
            KeiraAiMod.LOGGER.debug("AI broadcast request: locale={}, ctx='''\n{}\n'''", responseLocale, worldContext);

            String message = AiRuntime.AIClient
                .prompt()
                .system("You are Keira. Compose brief, relevant broadcast tips in the requested language only.")
                .user(prompt)
                .call()
                .content();

            long cost = System.currentTimeMillis() - start;
            if (cost > 15000) {
                KeiraAiMod.LOGGER.warn("AI broadcast completed (slow): {} ms", cost);
            } else {
                KeiraAiMod.LOGGER.info("AI broadcast completed: {} ms", cost);
            }

            server.execute(() -> server.getPlayerManager().getPlayerList().forEach(p ->
                Messages.overlay(p, Text.translatable("keira.auto.broadcast", message))
            ));

        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to generate AI broadcast message", e);
        }
    }

    // ---- Personalized ----

    private static void sendPersonalizedMessages() {
        if (!systemEnabled || !personalizedEnabled || !AiRuntime.isReady()) {
            return;
        }

        server.getPlayerManager().getPlayerList().forEach(player -> {
            String playerName = player.getName().getString();

            // per-player opt-out
            if (playerOptOut.getOrDefault(playerName, false)) return;

            // do not interrupt an ongoing AI chat session
            if (AiChatSystem.isInAiChatMode(playerName)) return;

            try {
                // Player context as English JSON for stable AI input
                String ctx = new PlayerContextBuilder().build(player);
                String responseLocale = PlayerLanguageCache.code(player);

                String prompt = String.format("""
You are Keira, a helpful in-server assistant.
Task: Provide a short, actionable, friendly tip tailored to the player's current status.

[Output Language]
Must be in the player's client language: %s (fallback en_us). No translation notes.

[Constraints]
- <= 100 characters.
- Be specific and practical.
- No Markdown, no code blocks.

[Player Context JSON]
%s
""", responseLocale, ctx);

                long start = System.currentTimeMillis();
                KeiraAiMod.LOGGER.debug("AI personal request: player={}, playerCtx={}, locale={}", playerName,ctx , responseLocale);

                String message = AiRuntime.AIClient
                    .prompt()
                    .system("You are Keira. Produce concise, practical, localized tips based on the given JSON context.")
                    .user(prompt)
                    .call()
                    .content();

                long cost = System.currentTimeMillis() - start;
                if (cost > 8000) {
                    KeiraAiMod.LOGGER.warn("AI personal completed (slow): player={}, {} ms", playerName, cost);
                } else {
                    KeiraAiMod.LOGGER.info("AI personal completed: player={}, {} ms", playerName, cost);
                }

                server.execute(() -> Messages.to(player, Text.translatable("keira.auto.personal", message)));

            } catch (Exception e) {
                KeiraAiMod.LOGGER.error("Failed to generate personalized message: " + playerName, e);
            }
        });
    }

    // ---- Context builders ----

    /** Collect a minimal English world context for AI prompts. */
    private static String gatherWorldContext() {
        StringBuilder context = new StringBuilder();

        int online = server.getPlayerManager().getPlayerList().size();
        context.append("online_players: ").append(online).append('\n');

        // overworld snapshot
        var overworld = server.getWorld(World.OVERWORLD);
        if (overworld != null) {
            long timeOfDay = overworld.getTimeOfDay() % 24000;
            String timeDesc = getTimeDescription(timeOfDay);
            context.append("overworld_time: ").append(timeDesc).append('\n');

            boolean isRaining = overworld.isRaining();
            boolean isThundering = overworld.isThundering();
            String weather = isThundering ? "thunder" : (isRaining ? "rain" : "clear");
            context.append("weather: ").append(weather).append('\n');
        }

        // player distribution by dimension
        long playersInOverworld = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.OVERWORLD)
            .count();
        long playersInNether = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.NETHER)
            .count();
        long playersInEnd = server.getPlayerManager().getPlayerList().stream()
            .filter(p -> p.getWorld().getRegistryKey() == World.END)
            .count();

        context.append(String.format("distribution: overworld=%d, nether=%d, end=%d",
            playersInOverworld, playersInNether, playersInEnd));

        return context.toString();
    }



    private static String getTimeDescription(long timeOfDay) {
        if (timeOfDay < 1000) return "dawn";
        if (timeOfDay < 6000) return "morning";
        if (timeOfDay < 12000) return "afternoon";
        if (timeOfDay < 18000) return "evening";
        return "night";
    }


    private static String majorityLocaleCode() {
        Map<String, Integer> counts = new HashMap<>();
        server.getPlayerManager().getPlayerList().forEach(p -> {
            String code = PlayerLanguageCache.code(p);
            counts.merge(code, 1, Integer::sum);
        });
        String best = "en_us";
        int bestCount = 0;
        for (var e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    /** Enable/disable the entire intelligent auto message system. */
    public static void toggleSystem(boolean enabled) { systemEnabled = enabled; }

    /** Back-compat: alias of toggleSystem. */
    public static void toggleAutoMessages(boolean enabled) { toggleSystem(enabled); }

    /** Per-player personalized tips opt-out (enabled=false means opt-out). */
    public static void togglePlayerAutoMessages(String playerName, boolean enabled) {
        playerOptOut.put(playerName, !enabled);
    }

    /** System on/off status. */
    public static boolean isSystemEnabled() { return systemEnabled; }
}
