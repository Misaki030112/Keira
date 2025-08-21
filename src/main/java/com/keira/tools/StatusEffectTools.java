package com.keira.tools;

import com.google.gson.*;
import com.keira.KeiraAiMod;
import com.keira.observability.RequestContext;
import com.keira.util.MainThread;
import com.keira.util.Messages;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Status Effect tools redesigned for AI:
 * - Full effect catalog with aliases and categories.
 * - Natural-language search with ranking and category filters.
 * - Structured JSON responses for all tools.
 * - Safe main-thread mutations via MainThread.
 */
public class StatusEffectTools {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;

    public StatusEffectTools(MinecraftServer server) { this.server = server; }

    /* ===================== 1) Catalog: expose everything AI can use ===================== */

    @Tool(name = "catalog_status_effects",
            description = """
        List all registered status effects with categories and aliases (for AI discovery).

        OUTPUT (JSON array)
          [
            {"id":"minecraft:speed","name":"Speed","category":"beneficial","aliases":["speed","swiftness","move","movement","run"]},
            {"id":"minecraft:water_breathing","name":"Water Breathing","category":"beneficial","aliases":["water","breath","underwater","drown","water_breathing"]},
            ...
          ]

        Notes:
          - 'name' is en_us server-side translation (stable for AI).
          - 'aliases' includes built-in synonyms for vanilla plus tokens from the id path.
          - Mods are supported: aliases fall back to id tokenization if not in the built-in lexicon.
        """)
    public String catalogStatusEffects() {
        long startNanos = System.nanoTime();
        List<JsonObject> out = EffectIndex.getOrBuild().stream().map(ce -> {
            JsonObject o = new JsonObject();
            o.addProperty("id", ce.id);
            o.addProperty("name", ce.name);
            o.addProperty("category", ce.category);
            JsonArray arr = new JsonArray();
            ce.aliases.forEach(arr::add);
            o.add("aliases", arr);
            return o;
        }).collect(Collectors.toList());
        String json = GSON.toJson(out);
        KeiraAiMod.LOGGER.debug("{} [tool:catalog_status_effects] count={} cost={}ms",
                RequestContext.midTag(), out.size(), (System.nanoTime()-startNanos)/1_000_000L);
        return json;
    }

    /* ===================== 2) Search: natural-language to effect id ===================== */

    @Tool(name = "search_status_effects",
            description = """
        Search status effects by natural language with AND semantics and optional category filter.

        INPUT
          - query: free text, e.g., "underwater breathe", "see in dark", "fire immune", "negative poison"
                  operators:
                    category:beneficial | category:neutral | category:harmful
                    negative | positive (shorthands mapping to category)
          - limit: optional (default 10, clamp 1..50)

        OUTPUT (JSON array)
          [
            {"id":"minecraft:water_breathing","name":"Water Breathing","category":"beneficial","score":210},
            ...
          ]
        """)
    public String searchStatusEffects(
            @ToolParam(description = "Query text with optional category: filter") String query,
            @ToolParam(description = "Max results (1..50), default 10") Integer limit
    ) {
        KeiraAiMod.LOGGER.debug("{} [tool:search_status_effects] params q='{}' limit={}",
                RequestContext.midTag(), query, limit);
        long startNanos = System.nanoTime();
        final int cap = Math.max(1, Math.min(50, (limit == null) ? 10 : limit));
        final Query q = Query.parse(query);

        List<EffectIndex.CE> all = EffectIndex.getOrBuild();
        ArrayList<Scored> buf = new ArrayList<>(cap * 4);

        for (EffectIndex.CE ce : all) {
            if (!q.acceptCategory(ce.category)) continue;

            int score = 0;
            boolean ok = q.tokens.isEmpty(); // empty query -> accept with minimal score
            for (String t : q.tokens) {
                int s = 0;
                // strong: id / path exact/prefix/contains
                if (ce.idLower.equals(t) || ce.pathLower.equals(t)) s = 120;
                else if (ce.idLower.startsWith(t) || ce.pathLower.startsWith(t)) s = 80;
                else if (ce.idLower.contains(t) || ce.pathLower.contains(t)) s = 50;

                // aliases / name
                if (s == 0) {
                    if (ce.aliasesLower.contains(t)) s = 90; // exact alias
                    else if (startsWithAny(ce.aliasesLower, t)) s = 55;
                    else if (containsAny(ce.aliasesLower, t)) s = 25;
                }
                if (s > 0) { ok = true; score += s; } else { ok = false; break; }
            }
            if (!ok) continue;

            buf.add(new Scored(score, ce));
            if (buf.size() >= cap * 8) break;
        }

        buf.sort((a, b) -> {
            int c = Integer.compare(b.score, a.score);
            return (c != 0) ? c : a.ce.id.compareTo(b.ce.id);
        });

        List<JsonObject> out = new ArrayList<>(Math.min(cap, buf.size()));
        for (int i = 0; i < buf.size() && i < cap; i++) {
            var ce = buf.get(i).ce;
            JsonObject o = new JsonObject();
            o.addProperty("id", ce.id);
            o.addProperty("name", ce.name);
            o.addProperty("category", ce.category);
            o.addProperty("score", buf.get(i).score);
            out.add(o);
        }
        String json = GSON.toJson(out);
        String preview = out.stream().limit(3).map(o->o.get("id").getAsString()).collect(Collectors.joining(", "));
        KeiraAiMod.LOGGER.debug("{} [tool:search_status_effects] q='{}' limit={} count={} cost={}ms preview=[{}]",
                RequestContext.midTag(), query, limit, out.size(), (System.nanoTime()-startNanos)/1_000_000L, preview);
        return json;
    }

    /* ===================== 3) List active effects on a player ===================== */

    @Tool(name = "list_status_effects",
            description = """
        List active status effects on a player (machine-friendly JSON).

        INPUT
          - playerName: player name or UUID (online only)

        OUTPUT (JSON array)
          [
            {"id":"minecraft:speed","name":"Speed","category":"beneficial","amplifier":1,"durationTicks":1200,"remainingSeconds":60},
            ...
          ]
        """)
    public String listStatusEffects(@ToolParam(description = "Player name or UUID") String playerName) {
        KeiraAiMod.LOGGER.debug("{} [tool:list_status_effects] player='{}'", RequestContext.midTag(), playerName);
        long startNanos = System.nanoTime();
        ServerPlayerEntity p = findPlayer(playerName);
        if (p == null) return "[]";

        String json = MainThread.callSync(server, () -> {
            JsonArray arr = new JsonArray();
            for (StatusEffectInstance e : p.getStatusEffects()) {
                RegistryEntry<StatusEffect> effEntry = e.getEffectType();
                StatusEffect eff = effEntry.value();
                Identifier id = Registries.STATUS_EFFECT.getId(eff);
                String name = id == null ? "unknown" : displayName(eff);
                String cat = category(eff);

                JsonObject o = new JsonObject();
                o.addProperty("id", id == null ? "unknown" : id.toString());
                o.addProperty("name", name);
                o.addProperty("category", cat);
                o.addProperty("amplifier", e.getAmplifier());
                o.addProperty("durationTicks", e.getDuration());
                o.addProperty("remainingSeconds", Math.max(0, e.getDuration() / 20));
                arr.add(o);
            }
            return GSON.toJson(arr);
        });
        int count = JsonParser.parseString(json).getAsJsonArray().size();
        KeiraAiMod.LOGGER.debug("{} [tool:list_status_effects] player='{}' count={} cost={}ms",
                RequestContext.midTag(), playerName, count, (System.nanoTime()-startNanos)/1_000_000L);
        return json;
    }

    /* ===================== 4) Apply effect (id or natural-language query) ===================== */

    @Tool(name = "apply_status_effect",
            description = """
        Apply a status effect to a player. The 'effect' input accepts either an exact id ("minecraft:speed")
        or a natural-language query ("see in dark", "underwater breathe"). The tool will search and use the top match.

        INPUT
          - playerName: player name or UUID (online only)
          - effect: id or query text
          - seconds: duration in seconds (default 30; clamp 1..3600)
          - amplifier: 0-based level (0=I, 1=II, ...; clamp 0..9)
          - ambient: optional (default false)
          - showParticles: optional (default true)
          - showIcon: optional (default true)

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "ERR_PLAYER_NOT_FOUND" | "ERR_EFFECT_RESOLVE" | "ERR_APPLY",
            "message": "...",
            "resolvedEffect": {"id":"minecraft:night_vision","name":"Night Vision","category":"beneficial"},
            "applied": {"amplifier":1,"seconds":60}
          }
        """)
    public String applyStatusEffect(
            @ToolParam(description = "Player name or UUID") String playerName,
            @ToolParam(description = "Effect id or natural-language query") String effect,
            @ToolParam(description = "Duration seconds (1..3600)") Integer seconds,
            @ToolParam(description = "Amplifier (0..9), 0 means level I") Integer amplifier,
            @ToolParam(description = "Ambient (default false)") Boolean ambient,
            @ToolParam(description = "Show particles (default true)") Boolean showParticles,
            @ToolParam(description = "Show icon (default true)") Boolean showIcon
    ) {
        long startNanos = System.nanoTime();
        KeiraAiMod.LOGGER.debug("{} [tool:apply_status_effect] player='{}' effect='{}' seconds={} amp={} ambient={} particles={} icon={}",
                RequestContext.midTag(), playerName, effect, seconds, amplifier, ambient, showParticles, showIcon);
        ServerPlayerEntity p = findPlayer(playerName);
        if (p == null) return jsonError("ERR_PLAYER_NOT_FOUND", "Player not found: " + playerName);

        // Resolve effect: try id first, then natural-language search
        EffectIndex.CE ce = resolveEffect(effect);
        if (ce == null) return jsonError("ERR_EFFECT_RESOLVE", "Cannot resolve effect from input: " + effect);

        int secs = clamp((seconds == null) ? 30 : seconds, 1, 3600);
        int amp = clamp((amplifier == null) ? 0 : amplifier, 0, 9);
        boolean amb = ambient != null && ambient;
        boolean particles = showParticles == null || showParticles;
        boolean icon = showIcon == null || showIcon;

        Identifier id = Identifier.tryParse(ce.id);
        if (id == null) return jsonError("ERR_EFFECT_RESOLVE", "Invalid effect id: " + ce.id);
        StatusEffect eff = Registries.STATUS_EFFECT.get(id);
        if (eff == null) return jsonError("ERR_EFFECT_RESOLVE", "Effect not found in registry: " + ce.id);
        RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(eff);

        AtomicReference<String> ref = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            boolean ok = p.addStatusEffect(new StatusEffectInstance(entry, secs * 20, amp, amb, particles, icon));
            if (!ok) {
                ref.set(jsonError("ERR_APPLY", "Game refused to apply (existing effect may be stronger)."));
                return;
            }
            // Send localized, client-side translatable message
            Text effName = effectDisplayText(id);
            Messages.to(p, Text.translatable("tool.effect.applied", effName, amp + 1, secs));
            JsonObject root = new JsonObject();
            root.addProperty("ok", true);
            root.addProperty("code", "OK");
            root.addProperty("message", "Applied");

            JsonObject resolved = new JsonObject();
            resolved.addProperty("id", ce.id);
            resolved.addProperty("name", ce.name);
            resolved.addProperty("category", ce.category);
            root.add("resolvedEffect", resolved);

            JsonObject applied = new JsonObject();
            applied.addProperty("amplifier", amp);
            applied.addProperty("seconds", secs);
            applied.addProperty("ambient", amb);
            applied.addProperty("showParticles", particles);
            applied.addProperty("showIcon", icon);
            root.add("applied", applied);

            ref.set(GSON.toJson(root));
        });
        String out = ref.get();
        KeiraAiMod.LOGGER.debug("{} [tool:apply_status_effect] player='{}' resolved='{}' amp={} secs={} cost={}ms",
                RequestContext.midTag(), playerName, ce.id, amp, secs, (System.nanoTime()-startNanos)/1_000_000L);
        return out;
    }

    /* ===================== 5) Clear effects (by category or specific ids/queries) ===================== */

    @Tool(name = "clear_status_effects",
            description = """
        Clear status effects from a player by category or specific list.

        INPUT
          - playerName: player name or UUID (online only)
          - mode: one of 'all', 'negative', 'positive' ('beneficial'), 'neutral', 'specific'
          - effects: when mode='specific', a comma-separated list of ids or natural-language queries

        OUTPUT (JSON)
          {"ok":true,"cleared":3,"details":["minecraft:poison","minecraft:wither","minecraft:blindness"]}
        """)
    public String clearStatusEffects(
            @ToolParam(description = "Player name or UUID") String playerName,
            @ToolParam(description = "all | negative | positive | neutral | specific") String mode,
            @ToolParam(description = "Comma-separated ids or queries when mode='specific'") String effects
    ) {
        long startNanos = System.nanoTime();
        KeiraAiMod.LOGGER.debug("{} [tool:clear_status_effects] player='{}' mode='{}' effects='{}'",
                RequestContext.midTag(), playerName, mode, effects);
        ServerPlayerEntity p = findPlayer(playerName);
        if (p == null) return jsonError("ERR_PLAYER_NOT_FOUND", "Player not found: " + playerName);

        String m = (mode == null) ? "all" : mode.trim().toLowerCase(Locale.ROOT);

        return MainThread.callSync(server, () -> {
            int cleared = 0;
            List<String> removed = new ArrayList<>();

            switch (m) {
                case "all" -> {
                    for (StatusEffectInstance e : List.copyOf(p.getStatusEffects())) {
                        var entry = e.getEffectType();
                        if (p.removeStatusEffect(entry)) {
                            cleared++;
                            idOf(entry).ifPresent(removed::add);
                        }
                    }
                    KeiraAiMod.LOGGER.debug("{} [tool:clear_status_effects] mode=all player='{}' cleared={}",
                            RequestContext.midTag(), playerName, cleared);
                    Messages.to(p, Text.translatable("tool.effect.cleared.all", cleared));
                    return jsonOkCleared(cleared, removed);
                }
                case "negative", "positive", "beneficial", "neutral" -> {
                    StatusEffectCategory want = switch (m) {
                        case "negative" -> StatusEffectCategory.HARMFUL;
                        case "beneficial", "positive" -> StatusEffectCategory.BENEFICIAL;
                        default -> StatusEffectCategory.NEUTRAL;
                    };
                    for (StatusEffectInstance e : List.copyOf(p.getStatusEffects())) {
                        var entry = e.getEffectType();
                        if (entry.value().getCategory() == want) {
                            if (p.removeStatusEffect(entry)) {
                                cleared++;
                                idOf(entry).ifPresent(removed::add);
                            }
                        }
                    }
                    KeiraAiMod.LOGGER.debug("{} [tool:clear_status_effects] mode={} player='{}' cleared={}",
                            RequestContext.midTag(), m, playerName, cleared);
                    Messages.to(p, Text.translatable(
                            m.equals("negative") ? "tool.effect.cleared.negative" : "tool.effect.cleared.specific", // neutral/positive fallback
                            cleared));
                    return jsonOkCleared(cleared, removed);
                }
                case "specific" -> {
                    if (effects != null && !effects.isBlank()) {
                        for (String tok : effects.split(",")) {
                            EffectIndex.CE ce = resolveEffect(tok.trim());
                            if (ce == null) continue;
                            Identifier id = Identifier.tryParse(ce.id);
                            if (id == null) continue;
                            StatusEffect eff = Registries.STATUS_EFFECT.get(id);
                            if (eff == null) continue;
                            RegistryEntry<StatusEffect> entry = Registries.STATUS_EFFECT.getEntry(eff);
                            if (p.removeStatusEffect(entry)) {
                                cleared++;
                                removed.add(ce.id);
                            }
                        }
                    }
                    KeiraAiMod.LOGGER.debug("{} [tool:clear_status_effects] mode=specific player='{}' cleared={} details={}",
                            RequestContext.midTag(), playerName, cleared, String.join(",", removed));
                    if (cleared > 0) Messages.to(p, Text.translatable("tool.effect.cleared.specific", cleared));
                    return jsonOkCleared(cleared, removed);
                }
            }

            String err = jsonError("ERR_BAD_MODE", "Invalid mode; use all|negative|positive|neutral|specific");
            KeiraAiMod.LOGGER.warn("{} [tool:clear_status_effects] bad mode='{}' cost={}ms",
                    RequestContext.midTag(), mode, (System.nanoTime()-startNanos)/1_000_000L);
            return err;
        });
    }

    /* ===================== helpers ===================== */

    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        try { UUID u = UUID.fromString(nameOrUuid); return server.getPlayerManager().getPlayer(u); }
        catch (Exception ignore) { return null; }
    }

    private static String category(StatusEffect e) {
        StatusEffectCategory c = e.getCategory();
        if (c == StatusEffectCategory.BENEFICIAL) return "beneficial";
        if (c == StatusEffectCategory.HARMFUL) return "harmful";
        return "neutral";
    }

    private static String displayName(StatusEffect e) {
        return net.minecraft.text.Text.translatable(e.getTranslationKey()).getString();
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static Optional<String> idOf(RegistryEntry<StatusEffect> entry) {
        return entry.getKey().map(RegistryKey::getValue).map(Identifier::toString);
    }

    private static String jsonError(String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("code", code);
        o.addProperty("message", message);
        return GSON.toJson(o);
    }

    private static String jsonOkCleared(int cleared, List<String> ids) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("cleared", cleared);
        JsonArray arr = new JsonArray();
        for (String s : ids) arr.add(s);
        o.add("details", arr);
        return GSON.toJson(o);
    }

    private static boolean startsWithAny(Set<String> set, String t) {
        for (String s : set) if (s.startsWith(t)) return true;
        return false;
    }

    private static boolean containsAny(Set<String> set, String t) {
        for (String s : set) if (s.contains(t)) return true;
        return false;
    }

    /* ===================== Effect index & lexicon ===================== */

    private static final class Scored {
        final int score; final EffectIndex.CE ce;
        Scored(int score, EffectIndex.CE ce) { this.score = score; this.ce = ce; }
    }

    /** Parse query into tokens + category filter. */
    private static final class Query {
        final List<String> tokens = new ArrayList<>();
        final String category; // "beneficial"|"neutral"|"harmful"|null

        static Query parse(String q) {
            if (q == null) return new Query(null, List.of());
            q = q.trim().toLowerCase(Locale.ROOT);
            String cat = null;
            List<String> toks = new ArrayList<>();
            for (String tok : q.split("\\s+")) {
                if (tok.startsWith("category:")) {
                    String v = tok.substring("category:".length());
                    if (v.equals("beneficial") || v.equals("neutral") || v.equals("harmful")) cat = v;
                }
                else if (tok.equals("positive") || tok.equals("beneficial")) { cat = "beneficial"; }
                else if (tok.equals("negative") || tok.equals("harmful")) { cat = "harmful"; }
                else if (!tok.isBlank()) toks.add(tok);
            }
            return new Query(cat, toks);
        }

        Query(String category, List<String> toks) {
            this.category = category;
            this.tokens.addAll(toks);
        }

        boolean acceptCategory(String effCat) {
            return category == null || category.equals(effCat);
        }
    }

    /** Effect index: immutable snapshot with aliases for search. */
    private static final class EffectIndex {
        private static volatile List<CE> SNAP = List.of();

        static List<CE> getOrBuild() {
            var s = SNAP;
            if (!s.isEmpty()) return s;
            synchronized (EffectIndex.class) {
                if (!SNAP.isEmpty()) return SNAP;
                ArrayList<CE> list = new ArrayList<>(Registries.STATUS_EFFECT.size());
                for (StatusEffect eff : Registries.STATUS_EFFECT) {
                    Identifier id = Registries.STATUS_EFFECT.getId(eff);
                    if (id == null) continue;
                    String idStr = id.toString();
                    String name = displayName(eff);
                    String cat = category(eff);
                    // aliases = builtin lexicon + tokens from id path (split underscores)
                    LinkedHashSet<String> aliases = new LinkedHashSet<>(Lexicon.aliases(idStr));
                    for (String p : id.getPath().split("[_\\-]+")) if (!p.isBlank()) aliases.add(p);
                    list.add(new CE(idStr, id.getPath(), name, cat, List.copyOf(aliases)));
                }
                SNAP = Collections.unmodifiableList(list);
                return SNAP;
            }
        }

        static final class CE {
            final String id;          // "minecraft:night_vision"
            final String path;        // "night_vision"
            final String name;        // "Night Vision" (en_us)
            final String category;    // beneficial|harmful|neutral
            final List<String> aliases;// ["night","vision","see","dark",...]
            // lowercase caches
            final String idLower, pathLower;
            final Set<String> aliasesLower;

            CE(String id, String path, String name, String category, List<String> aliases) {
                this.id = id;
                this.path = path;
                this.name = name;
                this.category = category;
                this.aliases = aliases;
                this.idLower = id.toLowerCase(Locale.ROOT);
                this.pathLower = path.toLowerCase(Locale.ROOT);
                this.aliasesLower = aliases.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
            }
        }
    }

    private static EffectIndex.CE resolveEffect(String input) {
        if (input == null || input.isBlank()) return null;
        input = input.trim();
        // exact id?
        Identifier id = Identifier.tryParse(input);
        if (id != null) {
            String idStr = id.toString();
            for (var ce : EffectIndex.getOrBuild()) if (ce.id.equals(idStr)) return ce;
        }
        // search
        String json = new StatusEffectTools(null).searchStatusEffects(input, 5); // no server access needed
        var arr = JsonParser.parseString(json).getAsJsonArray();
        if (arr.isEmpty()) return null;
        String id0 = arr.get(0).getAsJsonObject().get("id").getAsString();
        for (var ce : EffectIndex.getOrBuild()) if (ce.id.equals(id0)) return ce;
        return null;
    }

    /** Built-in synonyms for vanilla ids (extend as needed). */
    private static final class Lexicon {
        private static final Map<String, List<String>> MAP = new HashMap<>();
        static {
            put("minecraft:speed",               "speed,swiftness,move,movement,run,running");
            put("minecraft:slowness",            "slow,slowness,slowdown");
            put("minecraft:haste",               "haste,mining speed,fast mine");
            put("minecraft:mining_fatigue",      "fatigue,mining fatigue,slow mine");
            put("minecraft:strength",            "strength,damage boost,power,strong");
            put("minecraft:instant_health",      "instant health,heal now,heal instant");
            put("minecraft:instant_damage",      "instant damage,harm now,damage instant");
            put("minecraft:jump_boost",          "jump,jump boost,high jump");
            put("minecraft:nausea",              "nausea,drunk,wobble");
            put("minecraft:regeneration",        "regeneration,heal over time,hot,regen");
            put("minecraft:resistance",          "resistance,damage resist,protection");
            put("minecraft:fire_resistance",     "fire resistance,fire immune,lava immune,burn proof");
            put("minecraft:water_breathing",     "water breathing,underwater,breath water,drown");
            put("minecraft:invisibility",        "invisibility,invisible,stealth");
            put("minecraft:blindness",           "blind,blindness,dark screen");
            put("minecraft:night_vision",        "night vision,see in dark,bright");
            put("minecraft:hunger",              "hunger,starving");
            put("minecraft:weakness",            "weak,weakness,lower damage");
            put("minecraft:poison",              "poison,venom,damage over time");
            put("minecraft:wither",              "wither,decay,damage over time");
            put("minecraft:health_boost",        "health boost,more hearts");
            put("minecraft:absorption",          "absorption,extra hearts,shield hearts");
            put("minecraft:saturation",          "saturation,fill hunger");
            put("minecraft:glowing",             "glowing,outline,highlight");
            put("minecraft:levitation",          "levitation,float,fly up");
            put("minecraft:luck",                "luck,better loot");
            put("minecraft:unluck",              "unluck,bad luck,worse loot");
            put("minecraft:slow_falling",        "slow falling,feather fall,fall safe");
            put("minecraft:conduit_power",       "conduit power,underwater power,aquatic bonus");
            put("minecraft:dolphins_grace",      "dolphin grace,swim fast,water speed");
            put("minecraft:bad_omen",            "bad omen,raid trigger");
            put("minecraft:hero_of_the_village", "hero of the village,discount,raid victory");
            put("minecraft:darkness",            "darkness,darker screen,blind like warden");
        }
        static List<String> aliases(String id) {
            List<String> v = MAP.get(id);
            return (v == null) ? List.of() : v;
        }
        private static void put(String id, String csv) {
            MAP.put(id, Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList()));
        }
    }

    /** Build a client-localized effect display text from its registry id. */
    private static Text effectDisplayText(Identifier id) {
        if (id == null) return Text.of("unknown");
        String key = "effect." + id.getNamespace() + "." + id.getPath();
        return Text.translatable(key);
    }
}
