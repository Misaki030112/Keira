package com.hinadt.tools.items;

// ============================================================================
// Give Item Tool (Fabric 1.21.x) — redesigned for AI + performance
// - Structured JSON result with explicit fields (no string parsing)
// - Zero unnecessary ItemStack allocations for metadata
// - Uses PlayerInventory#insertStack to precisely measure inserted vs leftover
// - Safe drop behavior (no forced Y+5 teleport); optional gentle upward velocity
// - Main-thread enforcement wrapper included
// ============================================================================

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;


public class GiveItemTool {

    // ----------------------------- AI-facing result DTO -----------------------------
    public static record GiveItemResult(
            boolean ok,           // overall success flag
            String  code,         // "OK", "ERR_INVALID_ITEM_ID", "ERR_ITEM_NOT_FOUND", "ERR_PLAYER_OFFLINE", "ERR_BAD_COUNT"
            String  message,      // human-readable explanation (en_us)
            String  playerName,
            String  playerUuid,
            String  itemId,
            int     requested,    // requested count
            int     given,        // inserted into inventory
            int     dropped,      // dropped into world
            int     maxPerStack   // item max stack size
    ) {}

    @Tool(
            name = "give_item",
            description = """
        Give Item Tool — hand an item to a specific online player; overflow is dropped nearby.

        PURPOSE
        - Put as many items as possible directly into the player's inventory.
        - Any remainder (inventory full) is spawned as a world drop near the player.

        INPUT
          target (string, required)
            - Player name or UUID (online players only).
          item (string, required)
            - Exact item id in "namespace:path" (e.g., "minecraft:diamond_sword").
            - Use the search_items tool first if you need discovery.
          count (int, optional)
            - Default 1; clamped to [1..1024]. Very large counts may be throttled for server safety.

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "ERR_INVALID_ITEM_ID" | "ERR_ITEM_NOT_FOUND" | "ERR_PLAYER_OFFLINE" | "ERR_BAD_COUNT",
            "message": "<short explanation>",
            "playerName": "<name>",
            "playerUuid": "<uuid>",
            "itemId": "namespace:path",
            "requested": <int>,
            "given": <int>,
            "dropped": <int>,
            "maxPerStack": <int>
          }

        NOTES
          - Runs on the server main thread for world/inventory safety.
          - Uses PlayerInventory#insertStack to compute exact inserted vs leftover counts.
          - Drop behavior is safe by default; it does not force a specific Y offset.
        """
    )
    public static GiveItemResult giveItem(
            @ToolParam(description = "Online player name or UUID") String target,
            @ToolParam(description = "Exact item id, e.g., 'minecraft:diamond_sword'") String item,
            @ToolParam(description = "Item count (default 1, clamped to [1..1024])") Integer count
    ) {
        final int requested = clamp((count == null) ? 1 : count, 1, 1024);

        // --- Parse and resolve item id ---
        final Identifier id = Identifier.tryParse(item);
        if (id == null) {
            return new GiveItemResult(false, "ERR_INVALID_ITEM_ID",
                    "Item id must be 'namespace:path'.", null, null, item, requested, 0, 0, 0);
        }
        final Item mcItem = Registries.ITEM.get(id);
        if (mcItem == net.minecraft.item.Items.AIR) {
            return new GiveItemResult(false, "ERR_ITEM_NOT_FOUND",
                    "Item not found in registry.", null, null, item, requested, 0, 0, 0);
        }
        final int maxPerStack = mcItem.getMaxCount(); // light-weight, no ItemStack allocation

        // --- Resolve online player by name or UUID ---
        final ServerPlayerEntity player = findOnlinePlayer(target);
        if (player == null) {
            return new GiveItemResult(false, "ERR_PLAYER_OFFLINE",
                    "Target player is not online.", null, null, item, requested, 0, 0, maxPerStack);
        }

        // --- Execute on main thread and wait for completion ---
        final AtomicReference<GiveItemResult> ref = new AtomicReference<>();
        runOnMainAndWait(() -> {
            int remaining = requested;
            int given = 0;
            int dropped = 0;

            // Split into proper stacks and try insert first, drop leftovers if any
            while (remaining > 0) {
                int take = Math.min(maxPerStack, remaining);
                ItemStack stack = new ItemStack(mcItem, take);

                int before = stack.getCount();
                boolean fullyInserted = player.getInventory().insertStack(stack); // mutates 'stack' to leftover
                int after = stack.getCount();
                int inserted = before - after; // how many actually went in

                if (inserted > 0) given += inserted;

                if (after > 0) {
                    // There is leftover; drop it safely near the player.
                    // dropItem returns an ItemEntity or null (e.g., if world is invalid).
                    var droppedEntity = player.dropItem(stack.copy(), false);
                    if (droppedEntity != null) {
                        // Optional: give a tiny upward nudge so it's visually noticeable
                        droppedEntity.setVelocity(0, 0.15, 0);
                        dropped += stack.getCount();
                    }
                    // stack is only used to compute leftover; do not reuse
                }

                remaining -= take;
            }

            // Notify player (server is en_us as per your setup)
            player.sendMessage(Text.of("[AI] Item processed: " + item + " x" + requested +
                    " (given=" + given + ", dropped=" + dropped + ")"));

            ref.set(new GiveItemResult(true, "OK", "Item delivery finished.",
                    player.getGameProfile().getName(),
                    player.getUuid().toString(),
                    id.toString(),
                    requested, given, dropped, maxPerStack));
        });

        return ref.get();
    }

    // ----------------------------- Utilities -----------------------------

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    /** Find an online player by name first, then by UUID string. */
    private static ServerPlayerEntity findOnlinePlayer(String nameOrUuid) {
        MinecraftServer server = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameInstance() instanceof MinecraftServer s ? s : null;
        if (server == null) return null;

        // by name
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;

        // by UUID
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }

    /** Ensure the runnable runs on the server main thread; block until completion. */
    private static void runOnMainAndWait(Runnable task) {
        MinecraftServer server = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameInstance() instanceof MinecraftServer s ? s : null;
        if (server == null) { task.run(); return; }

        if (server.isOnThread()) { // already on main thread
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> { try { task.run(); } finally { latch.countDown(); } });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

}
