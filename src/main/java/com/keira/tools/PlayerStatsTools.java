package com.keira.tools;

import com.keira.KeiraAiMod;
import com.keira.observability.RequestContext;
import com.keira.util.MainThread;
import com.keira.util.Messages;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player stats tools
 * - Replies shown to players are localized via i18n keys when applicable
 * - All mutations run on main thread using MainThread
 */
public class PlayerStatsTools {

    private final MinecraftServer server;

    public PlayerStatsTools(MinecraftServer server) {
        this.server = server;
    }

    @Tool(
            name = "get_player_info",
            description = """
        Get a player's current status and location summary.

        INPUT
          - playerName: player name or UUID (online only)

        OUTPUT (plain English text; not JSON)
          - World (dimension id), coordinates (x,y,z)
          - Health (current/max), hunger (0..20), xp level
          - Game mode (localized name)

        Guidance for AI:
          - Use this to quickly inspect a player's survival readiness or to decide follow-up actions (heal, cleanse, buff).
          - For machine-readable details, combine with other tools as needed (e.g., list_status_effects).
        """
    )
    public String getPlayerInfo(@ToolParam(description = "Player name or UUID") String playerName) {
        KeiraAiMod.LOGGER.debug("{} [tool:get_player_info] player='{}'", RequestContext.midTag(), playerName);
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) return "❌ Player not found: " + playerName;

        AtomicReference<String> result = new AtomicReference<>("");
        MainThread.runSync(server, () -> {
            var w = player.getWorld();
            var pos = player.getBlockPos();
            String worldId = w.getRegistryKey().getValue().toString();
            int hp = (int) player.getHealth();
            int maxHp = (int) player.getMaxHealth();
            int food = player.getHungerManager().getFoodLevel();
            int xp = player.experienceLevel;
            String gm = player.interactionManager.getGameMode().getTranslatableName().getString();
            String out = String.format(Locale.ROOT,
                    "📊 Player: %s\n🌍 World: %s\n📍 Coords: (%d, %d, %d)\n❤️ Health: %d/%d\n🍗 Hunger: %d/20\n⭐ XP Level: %d\n🎮 Gamemode: %s",
                    player.getName().getString(), worldId, pos.getX(), pos.getY(), pos.getZ(), hp, maxHp, food, xp, gm);
            result.set(out);
        });
        return result.get();
    }

    @Tool(
            name = "heal_player",
            description = """
        Fully heal a player: set health and hunger to maximum and clear status effects.

        INPUT
          - playerName: player name or UUID (online only)

        Semantics and guidance for AI:
          - Map colloquial/role-play phrases like "full cleanse", "purify", "purge all", "治愈我", "全面净化" to this tool when the intention is full recovery.
          - This implementation clears all effects for simplicity. If beneficial effects must be preserved, re-apply them afterwards via apply_status_effect.
          - Sends a localized confirmation to the player.
        """
    )
    public String healPlayer(@ToolParam(description = "Player name or UUID") String playerName) {
        KeiraAiMod.LOGGER.debug("{} [tool:heal_player] player='{}'", RequestContext.midTag(), playerName);
        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) return "❌ Player not found: " + playerName;

        AtomicReference<String> result = new AtomicReference<>("");
        MainThread.runSync(server, () -> {
            try {
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0f);
                // Clear all effects; then re-apply beneficial? Simpler: clear all, then rely on AI follow-ups to reapply if needed
                player.clearStatusEffects();
                Messages.to(player, Text.translatable("tool.heal.done"));
                result.set("✅ Healed player to full (health and hunger).");
                KeiraAiMod.LOGGER.info("{} [tool:heal_player] done player='{}'", RequestContext.midTag(), player.getName().getString());
            } catch (Exception e) {
                result.set("❌ Heal failed: " + e.getMessage());
                KeiraAiMod.LOGGER.error("Heal player failed", e);
            }
        });
        return result.get();
    }

    @Tool(
            name = "list_online_players",
            description = """
        List current online players with world, coordinates, and health summary.

        OUTPUT (plain English text; not JSON)
          - Each line shows player name, world id, coordinates, and health.
        """
    )
    public String listOnlinePlayers() {
        AtomicReference<String> result = new AtomicReference<>("");
        MainThread.runSync(server, () -> {
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            if (players.isEmpty()) { result.set("📭 No players online"); return; }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("👥 Online players (%d):\n", players.size()));
            for (ServerPlayerEntity p : players) {
                var w = p.getWorld();
                var pos = p.getBlockPos();
                String worldId = w.getRegistryKey().getValue().toString();
                sb.append(String.format("• %s - %s (%d, %d, %d) ❤️%d/20\n",
                        p.getName().getString(), worldId, pos.getX(), pos.getY(), pos.getZ(), (int) p.getHealth()));
            }
            result.set(sb.toString());
            KeiraAiMod.LOGGER.info("{} [tool:list_online_players] count={}", RequestContext.midTag(), players.size());
        });
        return result.get();
    }

    // ---- helpers ----

    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        try { UUID u = UUID.fromString(nameOrUuid); return server.getPlayerManager().getPlayer(u); }
        catch (Exception ignore) { return null; }
    }
}
