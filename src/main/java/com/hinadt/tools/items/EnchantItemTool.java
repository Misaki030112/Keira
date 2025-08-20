package com.hinadt.tools.items;

import com.google.gson.*;
import com.hinadt.AusukaAiMod;
import com.hinadt.observability.RequestContext;
import com.hinadt.util.MainThread;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Enchanting & inventory-list tools (Fabric/Yarn 1.21.8).
 *
 * IMPORTANT: Yarn 1.21.8 uses WrapperLookup to access dynamic registries.
 * Use: server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
 */
public class EnchantItemTool {

    private final MinecraftServer server;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public EnchantItemTool(MinecraftServer server) { this.server = server; }

    // ----------------------------- DTOs -----------------------------
    public record EnchantSpec(String id, int level) {}
    public record EnchantChange(String id, int from, int to) {}
    public record EnchantResult(
            boolean ok,
            String  code,
            String  message,
            String  playerName,
            String  playerUuid,
            String  slot,
            String  itemId,
            List<EnchantChange> changes,
            Map<String,Integer> before,
            Map<String,Integer> after
    ) {}

    // ----------------------------- Tools -----------------------------
    @Tool(
        name = "enchant_item",
        description = """
ENCHANT an item in a specific player slot. Strictly validates applicability, max levels, and conflicts.
Use **slotSpec** to precisely pick the target item; DO NOT guess.

PARAMS
- target: online player name or UUID
- slotSpec (string): one of
    hand:main | hand:off
    hotbar:0..8
    inv:0..35            (0..8 hotbar, 9..35 main inventory)
    armor:head|armor:chest|armor:legs|armor:feet
- enchantsJson (string): JSON array of { "id":"minecraft:efficiency", "level":5 }
- allowUnsafe (bool, default false): if true, levels above max are allowed
- removeConflicts (bool, default false): if true, existing conflicting enchants are removed; otherwise error

BEHAVIOR
- Resolves enchantments via server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
- Rejects: unknown enchant id, level above max (unless allowUnsafe), not-applicable-to-item,
  and mutual conflicts (unless removeConflicts).
- Applies via Data Components (DataComponentTypes.ENCHANTMENTS).

RETURNS (JSON)
{
  "ok": true/false,
  "code": "OK" | "ERR_*",
  "message": "...",
  "playerName": "...",
  "playerUuid": "...",
  "slot": "hand:main",
  "itemId": "minecraft:diamond_pickaxe",
  "changes": [{"id":"minecraft:efficiency","from":3,"to":5}],
  "before": {"minecraft:efficiency":3},
  "after":  {"minecraft:efficiency":5,"minecraft:mending":1}
}

ERROR CODES
- ERR_NO_ENCHANTS, ERR_PARSE, ERR_PLAYER_OFFLINE, ERR_BAD_SLOT, ERR_EMPTY_SLOT,
  ERR_NO_REGISTRY, ERR_ENCHANT_ID, ERR_ENCHANT_NOT_FOUND, ERR_LEVEL_TOO_HIGH,
  ERR_NOT_APPLICABLE, ERR_CONFLICT
EXAMPLES
- Enchant main-hand pickaxe to Efficiency V + Mending:
  slotSpec="hand:main", enchantsJson='[{"id":"minecraft:efficiency","level":5},{"id":"minecraft:mending","level":1}]'
"""
    )
    public EnchantResult enchantItem(
            @ToolParam(description = "Online player name or UUID") String target,
            @ToolParam(description = "Slot spec: hand:main|hand:off|hotbar:0..8|inv:0..35|armor:head|armor:chest|armor:legs|armor:feet") String slotSpec,
            @ToolParam(description = "JSON array of {id,level}") String enchantsJson,
            @ToolParam(description = "Allow exceeding max level (default false)") Boolean allowUnsafe,
            @ToolParam(description = "Remove conflicting existing enchants (default false)") Boolean removeConflicts
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:enchant_item] args target='{}' slot='{}' allowUnsafe={} removeConflicts={} enchants={}",
                RequestContext.midTag(), target, slotSpec, allowUnsafe, removeConflicts, enchantsJson);

        if (enchantsJson == null || enchantsJson.isBlank()) {
            return new EnchantResult(false, "ERR_NO_ENCHANTS", "enchantsJson is required.", null, null, slotSpec, null, List.of(), Map.of(), Map.of());
        }
        final List<EnchantSpec> specs = parseSpecs(enchantsJson);
        if (specs.isEmpty()) {
            return new EnchantResult(false, "ERR_PARSE", "Invalid enchantsJson (expect array of {id,level}).", null, null, slotSpec, null, List.of(), Map.of(), Map.of());
        }
        final ServerPlayerEntity player = findOnlinePlayer(target);
        if (player == null) {
            return new EnchantResult(false, "ERR_PLAYER_OFFLINE", "Target player is not online.", null, null, slotSpec, null, List.of(), Map.of(), Map.of());
        }
        final boolean unsafe = Boolean.TRUE.equals(allowUnsafe);
        final boolean dropConflicts = Boolean.TRUE.equals(removeConflicts);

        final AtomicReference<EnchantResult> ref = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            final SlotRef refSlot = resolveSlot(player, slotSpec);
            if (refSlot == null) {
                ref.set(new EnchantResult(false, "ERR_BAD_SLOT", "Invalid slotSpec: " + slotSpec, player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, null, List.of(), Map.of(), Map.of()));
                return;
            }
            final ItemStack stack = refSlot.stack();
            if (stack.isEmpty()) {
                ref.set(new EnchantResult(false, "ERR_EMPTY_SLOT", "Slot is empty: " + slotSpec, player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, null, List.of(), Map.of(), Map.of()));
                return;
            }

            // Lookup registry (1.21.8 API)
            final RegistryWrapper.WrapperLookup lookup = server.getRegistryManager();
            final RegistryEntryLookup<Enchantment> enchLookup;
            try {
                enchLookup = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);
            } catch (Throwable t) {
                ref.set(new EnchantResult(false, "ERR_NO_REGISTRY", "Enchantment registry unavailable.", player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), List.of(), Map.of(), Map.of()));
                return;
            }

            // Resolve enchantments
            final List<ResolvedEnchant> toApply = new ArrayList<>();
            for (EnchantSpec s : specs) {
                Identifier id = Identifier.tryParse(s.id());
                if (id == null) {
                    ref.set(new EnchantResult(false, "ERR_ENCHANT_ID", "Invalid enchant id: " + s.id(), player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), List.of(), Map.of(), Map.of()));
                    return;
                }
                RegistryKey<Enchantment> key = RegistryKey.of(RegistryKeys.ENCHANTMENT, id);
                Optional<RegistryEntry.Reference<Enchantment>> optEntry = enchLookup.getOptional(key);
                if (optEntry.isEmpty()) {
                    ref.set(new EnchantResult(false, "ERR_ENCHANT_NOT_FOUND", "Enchantment not found: " + s.id(), player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), List.of(), Map.of(), Map.of()));
                    return;
                }
                RegistryEntry<Enchantment> entry = optEntry.get();
                Enchantment ench = entry.value();

                int lvl = Math.max(1, s.level());
                int max = ench.getMaxLevel();
                if (!unsafe && lvl > max) {
                    ref.set(new EnchantResult(false, "ERR_LEVEL_TOO_HIGH", "Level " + lvl + " exceeds max " + max + " for " + s.id(), player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), List.of(), Map.of(), Map.of()));
                    return;
                }
                if (!ench.isAcceptableItem(stack)) {
                    ref.set(new EnchantResult(false, "ERR_NOT_APPLICABLE", "Enchantment not applicable to this item: " + s.id(), player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), List.of(), Map.of(), Map.of()));
                    return;
                }
                toApply.add(new ResolvedEnchant(entry, lvl));
            }

            // Current enchants -> mutable map
            ItemEnchantmentsComponent current = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            Map<RegistryEntry<Enchantment>, Integer> cur = new LinkedHashMap<>();
            try {
                current.getEnchantmentEntries().forEach(e -> cur.put(e.getKey(), e.getIntValue()));
            } catch (Throwable ignored) {}

            // Conflicts
            for (ResolvedEnchant a : toApply) {
                for (RegistryEntry<Enchantment> b : new ArrayList<>(cur.keySet())) {
                    if (!Enchantment.canBeCombined(a.entry(), b)) {
                        if (dropConflicts) {
                            cur.remove(b);
                        } else {
                            ref.set(new EnchantResult(false, "ERR_CONFLICT",
                                    "Conflicts with existing enchant: " + entryId(b),
                                    player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack),
                                    List.of(), stringify(cur), stringify(cur)));
                            return;
                        }
                    }
                }
            }

            // Apply
            Map<String,Integer> before = stringify(cur);
            List<EnchantChange> changes = new ArrayList<>();
            for (ResolvedEnchant e : toApply) {
                String k = entryId(e.entry());
                int from = cur.getOrDefault(e.entry(), 0);
                cur.put(e.entry(), e.level());
                changes.add(new EnchantChange(k, from, e.level()));
            }

            // Rebuild data component
            ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            for (Map.Entry<RegistryEntry<Enchantment>, Integer> en : cur.entrySet()) {
                builder.add(en.getKey(), en.getValue());
            }
            stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());

            Map<String,Integer> after = stringify(cur);
            ref.set(new EnchantResult(true, "OK", "Applied " + toApply.size() + " enchant(s).",
                    player.getGameProfile().getName(), player.getUuidAsString(), slotSpec, idOf(stack), changes, before, after));
        });

        EnchantResult out = ref.get();
        AusukaAiMod.LOGGER.debug("{} [tool:enchant_item] result code={} msg='{}'",
                RequestContext.midTag(), out.code, out.message);
        return out;
    }

    @Tool(
        name = "list_inventory",
        description = """
LIST a player's inventory with stable slot labels so the AI/user can pick a precise target for enchanting or other actions.

PARAMS
- target: online player name or UUID
- filter (optional): case-insensitive substring match against id/path/display name
- limit (optional, default 50, clamp 1..200)
- offset (optional, default 0)

RETURNS (JSON array), each element:
{
  "slot": "hand:main|hand:off|hotbar:0..8|inv:0..35|armor:head|armor:chest|armor:legs|armor:feet",
  "slotIndex": 0,                    // only for hotbar/inv
  "id": "minecraft:diamond_pickaxe",
  "name": "Diamond Pickaxe",
  "count": 1,
  "durability": {"value":123,"max":1561},     // if damageable
  "enchants": {"minecraft:efficiency":5}      // if any
}

USAGE
1) Call list_inventory → show top N to the player
2) Player picks a slot → call enchant_item with that slot
"""
    )
    public String listInventory(
            @ToolParam(description = "Online player name or UUID") String target,
            @ToolParam(description = "Optional filter text") String filter,
            @ToolParam(description = "Limit (1..200), default 50") Integer limit,
            @ToolParam(description = "Offset, default 0") Integer offset
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:list_inventory] args target='{}' filter='{}' limit={} offset={}",
                RequestContext.midTag(), target, filter, limit, offset);
        final int cap = clamp((limit == null) ? 50 : limit, 1, 200);
        final int off = Math.max(0, (offset == null) ? 0 : offset);
        final String f = (filter == null) ? "" : filter.toLowerCase(Locale.ROOT).trim();

        final AtomicReference<String> ref = new AtomicReference<>("[]");
        final ServerPlayerEntity player = findOnlinePlayer(target);
        if (player == null) return "[]";

        MainThread.runSync(server, () -> {
            AusukaAiMod.LOGGER.debug("{} [tool:list_inventory] collecting stacks for player='{}'",
                    RequestContext.midTag(), player.getGameProfile().getName());
            List<JsonObject> rows = new ArrayList<>(64);
            addStack(rows, "hand:main",  -1, player.getMainHandStack());
            addStack(rows, "hand:off",   -1, player.getOffHandStack());
            addStack(rows, "armor:head", -1, player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
            addStack(rows, "armor:chest",-1, player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
            addStack(rows, "armor:legs", -1, player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
            addStack(rows, "armor:feet", -1, player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
            for (int i = 0; i <= 8; i++) addStack(rows, "hotbar:"+i, i, player.getInventory().getStack(i));
            for (int i = 9; i <= 35; i++) addStack(rows, "inv:"+i, i, player.getInventory().getStack(i));
            AusukaAiMod.LOGGER.debug("{} [tool:list_inventory] collected rows={} before filtering",
                    RequestContext.midTag(), rows.size());

            List<JsonObject> filtered = rows.stream().filter(o -> {
                if (f.isEmpty()) return true;
                String id = o.get("id").getAsString().toLowerCase(Locale.ROOT);
                String name = o.get("name").getAsString().toLowerCase(Locale.ROOT);
                return id.contains(f) || name.contains(f);
            }).toList();
            AusukaAiMod.LOGGER.debug("{} [tool:list_inventory] after filtering size={} filter='{}'",
                    RequestContext.midTag(), filtered.size(), f);

            int to = Math.min(filtered.size(), off + cap);
            JsonArray arr = new JsonArray();
            for (int i = off; i < to; i++) arr.add(filtered.get(i));
            String payload = GSON.toJson(arr);
            ref.set(payload);
            AusukaAiMod.LOGGER.debug("{} [tool:list_inventory] return items={} bytes={} preview={}",
                    RequestContext.midTag(), arr.size(), payload.length(), truncate(payload, 1200));
        });
        return ref.get();
    }

    // ----------------------------- Helpers -----------------------------

    private ServerPlayerEntity findOnlinePlayer(String nameOrUuid) {
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String idOf(ItemStack stack) {
        Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        return id.toString();
    }

    private static String entryId(RegistryEntry<Enchantment> e) {
        return e.getKey().map(k -> k.getValue().toString()).orElse("<unknown>");
    }

    private static Map<String,Integer> stringify(Map<RegistryEntry<Enchantment>, Integer> map) {
        Map<String,Integer> out = new LinkedHashMap<>();
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> e : map.entrySet()) {
            out.put(entryId(e.getKey()), e.getValue());
        }
        return out;
    }

    private static List<EnchantSpec> parseSpecs(String json) {
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            List<EnchantSpec> list = new ArrayList<>(arr.size());
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                int lvl = Math.max(1, o.get("level").getAsInt());
                list.add(new EnchantSpec(id, lvl));
            }
            return list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record ResolvedEnchant(RegistryEntry<Enchantment> entry, int level) {}

    private static final class SlotRef {
        private final ItemStack stack;
        SlotRef(ItemStack s) { this.stack = s; }
        ItemStack stack() { return stack; }
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    // Truncate long debug strings to avoid log spam
    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, Math.max(0, maxLen)) + "...<truncated>";
    }

    private static void addStack(List<JsonObject> rows, String slotLabel, int slotIndex, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        JsonObject o = new JsonObject();
        o.addProperty("slot", slotLabel);
        if (slotIndex >= 0) o.addProperty("slotIndex", slotIndex);
        o.addProperty("id", idOf(stack));
        o.addProperty("name", stack.getName().getString());
        o.addProperty("count", stack.getCount());
        if (stack.isDamageable()) {
            JsonObject d = new JsonObject();
            d.addProperty("value", stack.getDamage());
            d.addProperty("max", stack.getMaxDamage());
            o.add("durability", d);
        }
        try {
            ItemEnchantmentsComponent ench = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
            Map<String,Integer> m = new LinkedHashMap<>();
            ench.getEnchantmentEntries().forEach(e -> m.put(entryId(e.getKey()), e.getIntValue()));
            if (!m.isEmpty()) {
                JsonObject en = new JsonObject();
                for (var e : m.entrySet()) en.addProperty(e.getKey(), e.getValue());
                o.add("enchants", en);
            }
        } catch (Throwable ignore) {}
        rows.add(o);
    }

    private static SlotRef resolveSlot(ServerPlayerEntity p, String slotSpec) {
        if (slotSpec == null) return null;
        String s = slotSpec.trim().toLowerCase(Locale.ROOT);
        try {
            switch (s) {
                case "hand:main": return new SlotRef(p.getMainHandStack());
                case "hand:off":  return new SlotRef(p.getOffHandStack());
                case "armor:head":  return new SlotRef(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
                case "armor:chest": return new SlotRef(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
                case "armor:legs":  return new SlotRef(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
                case "armor:feet":  return new SlotRef(p.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
            }
            if (s.startsWith("hotbar:")) {
                int idx = Integer.parseInt(s.substring("hotbar:".length()));
                if (idx < 0 || idx > 8) return null;
                return new SlotRef(p.getInventory().getStack(idx));
            }
            if (s.startsWith("inv:")) {
                int idx = Integer.parseInt(s.substring("inv:".length()));
                if (idx < 0 || idx > 35) return null;
                return new SlotRef(p.getInventory().getStack(idx));
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
