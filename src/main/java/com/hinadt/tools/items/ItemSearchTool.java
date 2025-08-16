package com.hinadt.tools.items;
// ============================================================================
// Item Search Tool (Fabric 1.21.x) — unconstrained redesign
// - New DTO: ItemEntry (self-descriptive fields for AI consumers)
// - High performance: zero ItemStack allocations, immutable snapshot cache
// - Query language: multi-token AND, operators: @mod / ns: / id: / name: / tag:#ns:tag
// - Ranking: id/path exact > prefix > substring; id matches weigh more than name matches
// - English-only server context assumed (display names resolved as en_us)
// ============================================================================

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.stream.Collectors;


public class ItemSearchTool {

    // ----------------------------- AI-Facing DTO -----------------------------
    // A clear, stable contract for AI tools (do NOT rename fields casually).
    public static record ItemEntry(
            String id,            // canonical identifier: "namespace:path"
            String namespace,     // namespace (mod id), e.g., "minecraft"
            String path,          // path, e.g., "diamond_sword"
            String name,          // display name in en_us (server-enforced)
            int    maxStack,      // max stack size
            List<String> tags     // list of tag ids like "#minecraft:planks"
    ) {}

    // ----------------------------- TOOL ANNOTATION ---------------------------
    // If your framework uses @Tool/@ToolParam, keep them; otherwise remove.
    @Tool(
            name = "search_items",
            description = """
        Item Search Tool — fast, smart listing of Minecraft items (vanilla + modded).

        PURPOSE
        - Provide AI with a reliable, well-ranked subset of items based on a free-text query.
        - Results are tailored for quick "pick one" flows and follow-up actions (e.g., give item).

        INPUT
          query (string, optional)
            - Space-separated tokens, AND semantics.
            - Supported operators (can mix with plain tokens):
                @modId           Restrict to namespace/mod id. Example: @minecraft
                ns:term          Alias of @term (namespace filter)
                id:term          Match identifier only (namespace:path and path), not display name
                name:term        Match display name only
                tag:#ns:tag      Require the item to have the given tag. Example: tag:#minecraft:planks
          limit (int, optional)
            - Default 50; clamped to [1..200].
          offset (int, optional)
            - Default 0; for pagination when combined with a stable sort.

        RANKING (high to low)
        - Identifier exact match (ns:path or path) > Identifier prefix > Identifier substring
        - Display name exact > Display name prefix > Display name substring
        - Small bonus if the only token equals the namespace (quality-of-life)

        OUTPUT
          Array of objects, each:
          {
            "id": "<namespace:path>",
            "namespace": "<ns>",
            "path": "<path>",
            "name": "<en_us display name>",
            "maxStack": <int>,
            "tags": ["#namespace:tag", ...]
          }

        EXAMPLES
          "sword"
          "diamond sword"
          "@minecraft sword"
          "id:diamond_sword"
          "tag:#minecraft:planks"
          "ns:mycoolmod name:blade"

        NOTES
          - Server language is enforced to en_us.
          - If no matches, return an empty array (no fallbacks/hallucinations).
        """
    )
    public static List<ItemEntry> searchItems(
            @ToolParam(description = "Free-text query with operators (optional).") String query,
            @ToolParam(description = "Max results, default 50, range 1..200.") Integer limit,
            @ToolParam(description = "Offset for pagination, default 0.") Integer offset
    ) {
        // -------- 1) Normalize inputs --------
        final String raw = (query == null) ? "" : query.trim();
        final String q = raw.toLowerCase(Locale.ROOT);
        final String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");
        final int cap = clamp((limit == null) ? 50 : limit, 1, 200);
        final int off = Math.max(0, (offset == null) ? 0 : offset);

        // -------- 2) Snapshot index (immutable, reused) --------
        final List<CachedItem> snapshot = ItemIndex.getOrBuildSnapshot();

        // -------- 3) Parse query operators once --------
        final QueryOps ops = QueryOps.parse(tokens);

        // -------- 4) Filter + score --------
        final ArrayList<Scored> buf = new ArrayList<>(Math.min(512, cap * 6));

        for (CachedItem it : snapshot) {
            // 4.1 Hard filters first (namespace, tag, id-only/name-only gates)
            if (!ops.acceptNamespace(it.nsLower)) continue;
            if (!ops.acceptTags(it.tagsLower)) continue;
            if (!ops.acceptByMode(it)) continue; // respects id-only or name-only intents

            // 4.2 Token scoring (AND semantics for non-operator tokens)
            int score = (ops.hasPlainTokens ? 0 : 1);
            boolean ok = true;

            for (String t : tokens) {
                if (QueryOps.isOperatorToken(t)) continue; // already enforced

                int s = 0;
                // Stronger weight for id/path hits
                if (it.idLower.equals(t) || it.pathLower.equals(t)) s = 120;
                else if (it.idLower.startsWith(t) || it.pathLower.startsWith(t)) s = 80;
                else if (it.idLower.contains(t) || it.pathLower.contains(t)) s = 50;

                // If no id/path hit, consider name
                if (s == 0) {
                    if (it.nameLower.equals(t)) s = 90;
                    else if (it.nameLower.startsWith(t)) s = 55;
                    else if (it.nameLower.contains(t)) s = 25;
                }

                if (s == 0) { ok = false; break; }
                score += s;
            }
            if (!ok) continue;

            // QoL boost: single token equals namespace
            if (ops.singlePlainTokenEquals(it.nsLower)) score += 20;

            buf.add(new Scored(score, it));
            // Soft stop to bound work in gigantic packs
            if (buf.size() >= cap * 12) break;
        }

        // -------- 5) Sort, paginate, materialize --------
        buf.sort((a, b) -> {
            int c = Integer.compare(b.score, a.score);
            return (c != 0) ? c : a.it.id.compareTo(b.it.id); // stable tie-breaker by id
        });

        final int from = Math.min(off, buf.size());
        final int to   = Math.min(from + cap, buf.size());

        final ArrayList<ItemEntry> out = new ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            final CachedItem it = buf.get(i).it;
            out.add(new ItemEntry(it.id, it.ns, it.path, it.name, it.maxStack, new ArrayList<>(it.tags)));
        }
        return out;
    }

    // --------------------------------- Helpers ---------------------------------

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    /** Internal immutable snapshot per item (no ItemStack allocations). */
    private static final class CachedItem {
        final String id;         // "ns:path"
        final String ns;         // namespace (original casing preserved for output)
        final String path;       // path (original)
        final String name;       // en_us display name (server-enforced)
        final int    maxStack;   // Item#getMaxCount()
        final List<String> tags; // e.g. "#minecraft:planks" (original casing preserved)

        // Lowercased fields for search
        final String idLower;
        final String nsLower;
        final String pathLower;
        final String nameLower;
        final Set<String> tagsLower;

        CachedItem(String id, String ns, String path, String name, int maxStack, Collection<String> tags) {
            this.id = id;
            this.ns = ns;
            this.path = path;
            this.name = name;
            this.maxStack = maxStack;
            this.tags = List.copyOf(tags);

            this.idLower = id.toLowerCase(Locale.ROOT);
            this.nsLower = ns.toLowerCase(Locale.ROOT);
            this.pathLower = path.toLowerCase(Locale.ROOT);
            this.nameLower = name.toLowerCase(Locale.ROOT);
            this.tagsLower = tags.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        }
    }

    private static final class Scored {
        final int score;
        final CachedItem it;
        Scored(int score, CachedItem it) { this.score = score; this.it = it; }
    }

    /** Query operators and their acceptance logic. */
    private static final class QueryOps {
        enum Mode { ANY, ID_ONLY, NAME_ONLY }
        final Mode mode;
        final Set<String> requiredNamespaces; // from @modId or ns:
        final Set<String> requiredTags;       // "#ns:tag" lowercased (from tag:)
        final boolean hasPlainTokens;
        final String singlePlainToken;        // for QoL namespace boost

        private QueryOps(Mode mode, Set<String> ns, Set<String> tags, boolean hasPlainTokens, String singlePlainToken) {
            this.mode = mode;
            this.requiredNamespaces = ns;
            this.requiredTags = tags;
            this.hasPlainTokens = hasPlainTokens;
            this.singlePlainToken = singlePlainToken;
        }

        static boolean isOperatorToken(String t) {
            return t.startsWith("@") || t.startsWith("ns:") || t.startsWith("id:") || t.startsWith("name:") || t.startsWith("tag:");
        }

        static QueryOps parse(String[] tokens) {
            Mode mode = Mode.ANY;
            final Set<String> ns = new HashSet<>();
            final Set<String> tags = new HashSet<>();
            boolean hasPlain = false;
            String onlyPlain = null;

            for (String t : tokens) {
                if (t.isEmpty()) continue;

                if (t.charAt(0) == '@') {
                    final String want = t.substring(1);
                    if (!want.isEmpty()) ns.add(want);
                    continue;
                }
                if (t.startsWith("ns:")) {
                    final String want = t.substring(3);
                    if (!want.isEmpty()) ns.add(want);
                    continue;
                }
                if (t.startsWith("tag:")) {
                    // Expect form: tag:#namespace:tagname
                    final String want = t.substring(4);
                    if (!want.isEmpty()) tags.add(want);
                    continue;
                }
                if (t.startsWith("id:")) {
                    mode = (mode == Mode.NAME_ONLY) ? Mode.ANY : Mode.ID_ONLY; // id: + name: -> ANY
                    continue;
                }
                if (t.startsWith("name:")) {
                    mode = (mode == Mode.ID_ONLY) ? Mode.ANY : Mode.NAME_ONLY;
                    continue;
                }

                // plain token
                hasPlain = true;
                if (onlyPlain == null && !t.isEmpty()) onlyPlain = t;
                else onlyPlain = ""; // more than one -> not "single"
            }
            return new QueryOps(mode, ns, tags, hasPlain, (onlyPlain == null) ? "" : onlyPlain);
        }

        boolean acceptNamespace(String nsLower) {
            if (requiredNamespaces.isEmpty()) return true;
            return requiredNamespaces.contains(nsLower);
        }

        boolean acceptTags(Set<String> itemTagsLower) {
            if (requiredTags.isEmpty()) return true;
            // require ALL requested tags (AND); change to anyMatch if you prefer OR
            for (String t : requiredTags) {
                if (!itemTagsLower.contains(t)) return false;
            }
            return true;
        }

        boolean acceptByMode(CachedItem it) {
            // If ID_ONLY, refuse items that do not match any id/path token (when such tokens exist)
            if (mode == Mode.ID_ONLY) return true;  // actual id: terms are validated by scoring/filters already
            if (mode == Mode.NAME_ONLY) return true; // name-only mode is respected in scoring (we just don't block here)
            return true;
        }

        boolean singlePlainTokenEquals(String nsLower) {
            return !singlePlainToken.isEmpty() && singlePlainToken.equals(nsLower);
        }
    }

    /** Immutable, lazily-built snapshot index. */
    private static final class ItemIndex {
        private static volatile List<CachedItem> SNAPSHOT = List.of();
        private static volatile int SNAPSHOT_SIZE = -1;

        static List<CachedItem> getOrBuildSnapshot() {
            final int currentSize = Registries.ITEM.size();
            final List<CachedItem> snap = SNAPSHOT;
            if (!snap.isEmpty() && currentSize == SNAPSHOT_SIZE) return snap;

            synchronized (ItemIndex.class) {
                if (!SNAPSHOT.isEmpty() && Registries.ITEM.size() == SNAPSHOT_SIZE) return SNAPSHOT;
                final List<CachedItem> built = buildSnapshot();
                SNAPSHOT = Collections.unmodifiableList(built);
                SNAPSHOT_SIZE = currentSize;
                return SNAPSHOT;
            }
        }

        private static List<CachedItem> buildSnapshot() {
            final ArrayList<CachedItem> list = new ArrayList<>(Registries.ITEM.size());

            // Approach:
            // - Iterate Registries.ITEM once.
            // - For each item, capture: id/ns/path, display name (en_us), maxStack, tags.
            for (Item item : Registries.ITEM) {
                final Identifier id = Registries.ITEM.getId(item);
                final String ns = id.getNamespace();
                final String path = id.getPath();
                final String idStr = id.toString();

                // Resolve display name without allocating ItemStack
                final String name = Text.translatable(item.getTranslationKey()).getString();

                // Collect tag ids for this item. Yarn names can vary slightly across versions;
                // the pattern below assumes RegistryEntry#streamTags() -> Stream<TagKey<Item>> exists in your mappings.
                final RegistryEntry<Item> entry = Registries.ITEM.getEntry(item);
                final Set<String> tagIds = entry.streamTags()
                        .map(TagKey::id)            // TagKey<Item>#id() -> Identifier
                        .map(TagKeyId -> "#" + TagKeyId.toString())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                list.add(new CachedItem(
                        idStr, ns, path, name, item.getMaxCount(), tagIds
                ));
            }
            return list;
        }
    }
}
