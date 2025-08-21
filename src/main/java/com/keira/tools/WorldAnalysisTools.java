package com.keira.tools;

import com.keira.KeiraAiMod;
import com.keira.observability.RequestContext;
import com.keira.util.MainThread;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.stream.Collectors;

/**
 * World analysis tools — deeply refactored, safe, and fast.
 *
 * Design goals:
 * - Server-thread safety via MainThread helpers.
 * - Sampling-based scanning + hard budgets to avoid stalls.
 * - Stable identifiers for biome/block/entity names.
 * - Clear, compact, English output.
 */
@SuppressWarnings("resource")
public class WorldAnalysisTools {

    // ---------- Limits & Defaults ----------
    private static final int MAX_RADIUS_ANALYZE   = 64;
    private static final int MAX_RADIUS_RESOURCES = 96;
    private static final int DEF_RADIUS_ANALYZE   = 16;
    private static final int DEF_RADIUS_RESOURCE  = 32;

    // Work budgets to keep a single invocation lightweight
    private static final int MAX_BLOCK_CHECKS   = 60_000;  // max block state reads per call
    private static final int MAX_ENTITY_REPORT  = 6;       // top-K entity kinds to print
    private static final int MAX_POS_RESULTS    = 128;

// === BEGIN PATCH: dynamic stride for far-view sampling ===
private static final int TARGET_AXIS_ANALYZE  = 24; // analyze_surroundings sampling density per axis
private static final int TARGET_AXIS_ORE      = 16; // 3D ore grid density per axis
private static final int TARGET_AXIS_SURFACE  = 20; // near-surface (wood/water/lava) density per axis

private static int strideFor(int radius, int targetAxisSamples) {
    int step = Math.round((float) radius / (float) targetAxisSamples);
    return Math.max(2, step);
}
// === END PATCH ===
     // absolute cap for positions we list
    private static final int SURFACE_Y_DEPTH    = 6;       // how deep we peek below surface when sampling

    private final MinecraftServer server;

    public WorldAnalysisTools(MinecraftServer server) {
        this.server = server;
    }

    // ============================================================
    // analyze_surroundings
    // ============================================================
    @Tool(
            name = "analyze_surroundings",
            description = """
        Analyze the environment around a player (sampling-based).
        Reports: biome, height, weather/time, top nearby blocks (sampled), nearby entities (grouped), and a short suggestion.
        Radius: default 16, max 24. Internal budgets prevent server stalls.
        """
    )
    public String analyzeSurroundings(
            @ToolParam(description = "Player name or UUID") String playerName,
            @ToolParam(description = "Scan radius (default 16, max 24)") Integer radius
    ) {
        final String mid = RequestContext.midTag();
        KeiraAiMod.LOGGER.debug("{} [tool:analyze_surroundings] player='{}' radius={}", mid, playerName, radius);

        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) return "❌ Player not found: " + playerName;

        final int r = clamp(radius, DEF_RADIUS_ANALYZE, 1, MAX_RADIUS_ANALYZE);

        return MainThread.callSync(server, () -> {
            try {
                ServerWorld world = player.getWorld();
                BlockPos pos = player.getBlockPos();
                StringBuilder out = new StringBuilder(256);

                // Biome / height / weather / time
                String biomeName = getBiomeDisplayName(world, pos);
                int y = pos.getY();
                String heightInfo = getHeightBand(y);
                String weather = world.isRaining() ? (world.isThundering() ? "Thunderstorm" : "Rain") : "Clear";
                String tod = getTimeOfDay(world.getTimeOfDay());

                out.append("🔎 Environment analysis for ").append(player.getName().getString()).append('\n');
                out.append("🌍 Biome: ").append(biomeName).append('\n');
                out.append("📏 Height: Y=").append(y).append(" (").append(heightInfo).append(")\n");
                out.append("🌤️ Weather: ").append(weather).append(", Time: ").append(tod).append('\n');

                // Sampled top blocks (surface-focused)
                Map<Block, Integer> topBlocks = sampleTopBlocks(world, pos, r);
                if (!topBlocks.isEmpty()) {
                    out.append("🧱 Top nearby blocks:\n");
                    topBlocks.entrySet().stream()
                            .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                            .limit(5)
                            .forEach(e -> out.append("  • ")
                                    .append(getBlockDisplayName(e.getKey()))
                                    .append(" ×").append(e.getValue()).append('\n'));
                }

                // Nearby entities (non-player, grouped)
                List<String> entityReport = listNearbyEntities(world, pos, r);
                if (!entityReport.isEmpty()) {
                    out.append("🐾 Nearby entities: ").append(String.join(", ", entityReport)).append('\n');
                }

                // Suggestion
                String suggestion = suggest(world, pos, y, topBlocks);
                if (!isBlank(suggestion)) {
                    out.append("💡 Suggestion: ").append(suggestion);
                }

                KeiraAiMod.LOGGER.debug("{} [tool:analyze_surroundings] biome='{}' r={}", mid, biomeName, r);
                return out.toString();
            } catch (Exception e) {
                KeiraAiMod.LOGGER.error("[tool:analyze_surroundings] error", e);
                return "❌ Error during analysis: " + e.getMessage();
            }
        });
    }

    // ============================================================
    // find_resources
    // ============================================================
    @Tool(
            name = "find_resources",
            description = """
        Search for resources around a player.
        Types: ore / wood / water / lava / village (aliases supported: 矿物, 木材, 水源, 岩浆, 村庄).
        Returns up to 128 positions with distance and direction. Internal budgets apply.
        """
    )
    public String findResources(
            @ToolParam(description = "Player name or UUID") String playerName,
            @ToolParam(description = "Resource type") String resourceType,
            @ToolParam(description = "Search radius (default 32, max 48)") Integer radius
    ) {
        final String mid = RequestContext.midTag();
        KeiraAiMod.LOGGER.debug("{} [tool:find_resources] player='{}' type='{}' radius={}", mid, playerName, resourceType, radius);

        ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) return "❌ Player not found: " + playerName;

        final String type = normalizeType(resourceType);
        if (type == null) return "❌ Unknown resource type: " + resourceType;

        // ores are expensive → clamp harder
        final int req = clamp(radius, DEF_RADIUS_RESOURCE, 4, "ore".equals(type) ? 32 : MAX_RADIUS_RESOURCES);

        return MainThread.callSync(server, () -> {
            try {
                ServerWorld world = player.getWorld();
                BlockPos center = player.getBlockPos();
                List<BlockPos> found;

                switch (type) {
                    case "ore"    -> found = searchOres(world, center, req);
                    case "wood"   -> found = searchWoodSurface(world, center, req);
                    case "water"  -> found = searchFluidSurface(world, center, req, true);
                    case "lava"   -> found = searchFluidSurface(world, center, req, false);
                    case "village"-> found = searchVillageHints(world, center, req);
                    default       -> { return "❌ Unknown resource type: " + resourceType; }
                }

                if (found.isEmpty()) return "🔍 No " + type + " found within " + req + " blocks";

                // Sort by distance and cap output
                found = found.stream()
                        .sorted(Comparator.comparingDouble(p -> center.getSquaredDistance(p)))
                        .limit(Math.min(found.size(), MAX_POS_RESULTS))
                        .collect(Collectors.toList());

                StringBuilder out = new StringBuilder(256);
                out.append("🎯 Found ").append(found.size()).append(" ").append(type).append(" locations:\n");
                for (BlockPos p : found) {
                    int d = (int) Math.sqrt(center.getSquaredDistance(p));
                    String dir = directionOf(center, p);
                    out.append("• ").append(d).append(" blocks, ").append(dir)
                            .append(" (").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ()).append(")\n");
                }
                return out.toString();
            } catch (Exception e) {
                KeiraAiMod.LOGGER.error("[tool:find_resources] error", e);
                return "❌ Error during search: " + e.getMessage();
            }
        });
    }

    // ============================================================
    // Sampling / Searches
    // ============================================================

    /** Surface-oriented sampling: scan (x,z) grid, peek SURFACE_Y_DEPTH blocks downward. */
    private Map<Block, Integer> sampleTopBlocks(ServerWorld world, BlockPos center, int radius) {
        Map<Block, Integer> counts = new HashMap<>();
        final Mutable m = new Mutable();
        int step = strideFor(radius, TARGET_AXIS_ORE); // PATCH: keep cost ~constant at larger radii
        int checks = 0;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (checks >= MAX_BLOCK_CHECKS) break;

                int x = center.getX() + dx;
                int z = center.getZ() + dz;

                if (!isChunkLoaded(world, x, z)) continue;

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int minY = Math.max(world.getBottomY(), topY - SURFACE_Y_DEPTH);

                for (int y = topY; y >= minY; y--) {
                    if (checks++ >= MAX_BLOCK_CHECKS) break;
                    m.set(x, y, z);
                    Block b = world.getBlockState(m).getBlock();
                    if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                    counts.merge(b, 1, Integer::sum);
                    break; // only the first non-air surface block at this column
                }
            }
        }
        return counts;
    }

    /** Group nearby non-player entities within a radius; return top kinds by count. */
    private List<String> listNearbyEntities(ServerWorld world, BlockPos center, int radius) {
        Box box = new Box(center).expand(radius);
        Map<String, Integer> grouped = new HashMap<>();
        for (Entity e : world.getOtherEntities(null, box)) {
            if (e instanceof PlayerEntity) continue;
            String id = getEntityKindId(e);
            grouped.merge(id, 1, Integer::sum);
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_ENTITY_REPORT)
                .map(e -> e.getKey() + (e.getValue() > 1 ? " ×" + e.getValue() : ""))
                .collect(Collectors.toList());
    }

    /** Heuristic ore search with budget: coarse 3D sampling in a cube around center. */
    private List<BlockPos> searchOres(ServerWorld world, BlockPos center, int radius) {
        Set<Block> oreSet = oreBlocks();
        List<BlockPos> out = new ArrayList<>();
        final Mutable m = new Mutable();

        // coarse steps: shrink as radius shrinks
        final int step = Math.max(1, Math.min(6, Math.max(2, radius / 8)));
        int checks = 0;

        int worldMinY = world.getBottomY();
        int worldMaxY = worldMinY + world.getDimension().height() - 1;
        int minY = Math.max(worldMinY, center.getY() - radius);
        int maxY = Math.min(worldMaxY, center.getY() + radius);

        for (int x = center.getX() - radius; x <= center.getX() + radius; x += step) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += step) {
                if (!isChunkLoaded(world, x, z)) continue;
                for (int y = minY; y <= maxY; y += step) {
                    if (checks++ >= MAX_BLOCK_CHECKS || out.size() >= MAX_POS_RESULTS) return out;
                    m.set(x, y, z);
                    Block b = world.getBlockState(m).getBlock();
                    if (oreSet.contains(b)) out.add(m.toImmutable());
                }
            }
        }
        return out;
    }

    /** Find logs at surface columns (treat *_log & stems as wood). */
    private List<BlockPos> searchWoodSurface(ServerWorld world, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        final Mutable m = new Mutable();
        int checks = 0;
        final int step = Math.max(1, Math.min(4, radius / 8));

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (checks >= MAX_BLOCK_CHECKS || out.size() >= MAX_POS_RESULTS) return out;

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!isChunkLoaded(world, x, z)) continue;

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int minY = Math.max(world.getBottomY(), topY - SURFACE_Y_DEPTH);

                for (int y = topY; y >= minY; y--) {
                    checks++;
                    m.set(x, y, z);
                    Block b = world.getBlockState(m).getBlock();
                    String path = idPath(Registries.BLOCK.getId(b));
                    if (path.endsWith("_log") || path.endsWith("_stem")) {
                        out.add(m.toImmutable());
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Find surface fluids (water or lava) by peeking at top columns. */
    private List<BlockPos> searchFluidSurface(ServerWorld world, BlockPos center, int radius, boolean water) {
        List<BlockPos> out = new ArrayList<>();
        final Mutable m = new Mutable();
        int checks = 0;
        final int step = Math.max(1, Math.min(4, radius / 8));

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (checks >= MAX_BLOCK_CHECKS || out.size() >= MAX_POS_RESULTS) return out;

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!isChunkLoaded(world, x, z)) continue;

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int minY = Math.max(world.getBottomY(), topY - SURFACE_Y_DEPTH);

                for (int y = topY; y >= minY; y--) {
                    checks++;
                    m.set(x, y, z);
                    Block b = world.getBlockState(m).getBlock();
                    if (water && b == Blocks.WATER) { out.add(m.toImmutable()); break; }
                    if (!water && b == Blocks.LAVA) { out.add(m.toImmutable()); break; }
                }
            }
        }
        return out;
    }

    /** Village hints: nearby villagers, beds, and job-site blocks around surface. */
    private List<BlockPos> searchVillageHints(ServerWorld world, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();

        // 1) Villagers in radius
        for (Entity e : world.getOtherEntities(null, new Box(center).expand(radius))) {
            if (e instanceof VillagerEntity v) {
                out.add(v.getBlockPos());
                if (out.size() >= MAX_POS_RESULTS) return out;
            }
        }

        // 2) Beds on/near surface (light sampling)
        out.addAll(searchBedsNearSurface(world, center, radius, Math.max(1, radius / 10)));
        if (out.size() > MAX_POS_RESULTS) {
            return out.subList(0, MAX_POS_RESULTS);
        }

        return out;
    }

    private List<BlockPos> searchBedsNearSurface(ServerWorld world, BlockPos center, int radius, int step) {
        List<BlockPos> out = new ArrayList<>();
        final Mutable m = new Mutable();
        int checks = 0;

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (checks >= MAX_BLOCK_CHECKS || out.size() >= MAX_POS_RESULTS) return out;

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!isChunkLoaded(world, x, z)) continue;

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int minY = Math.max(world.getBottomY(), topY - SURFACE_Y_DEPTH);

                for (int y = topY; y >= minY; y--) {
                    checks++;
                    m.set(x, y, z);
                    if (world.getBlockState(m).getBlock() instanceof BedBlock) {
                        out.add(m.toImmutable());
                        break;
                    }
                }
            }
        }
        return out;
    }

    // ============================================================
    // Naming / Utils
    // ============================================================

    private String getBiomeDisplayName(ServerWorld world, BlockPos pos) {
        RegistryEntry<Biome> entry = world.getBiome(pos);
        Identifier id = entry.getKey().map(k -> k.getValue()).orElse(null);
        String path = id != null ? id.getPath() : entry.toString();
        return switch (path) {
            case "plains" -> "Plains";
            case "forest" -> "Forest";
            case "desert" -> "Desert";
            case "windswept_hills", "mountains" -> "Mountains";
            case "ocean" -> "Ocean";
            case "river" -> "River";
            case "swamp" -> "Swamp";
            case "taiga" -> "Taiga";
            case "savanna" -> "Savanna";
            case "badlands" -> "Badlands";
            case "jungle" -> "Jungle";
            case "snowy_plains", "ice_spikes" -> "Snowy Plains";
            case "nether_wastes" -> "Nether Wastes";
            case "the_end" -> "The End";
            default -> path.replace('_', ' ');
        };
    }

    private String getBlockDisplayName(Block block) {
        return idPath(Registries.BLOCK.getId(block)).replace('_', ' ');
    }

    private String getEntityKindId(Entity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (entity instanceof VillagerEntity) return "villager";
        return id != null ? id.getPath() : entity.getType().toString();
    }

    private String directionOf(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        boolean eastWest = Math.abs(dx) >= Math.abs(dz);
        if (eastWest) return dx >= 0 ? "East" : "West";
        else          return dz >= 0 ? "South" : "North";
    }

    private String getTimeOfDay(long time) {
        long t = time % 24000L;
        if (t < 6000)  return "Morning";
        if (t < 12000) return "Noon";
        if (t < 18000) return "Night";
        return "Midnight";
    }

    private String getHeightBand(int y) {
        if (y < 0)   return "Deep underground";
        if (y < 16)  return "Lower underground";
        if (y < 64)  return "Underground";
        if (y < 80)  return "Surface";
        if (y < 120) return "Highland";
        if (y < 200) return "Mountain";
        return "Sky";
    }

    private String suggest(ServerWorld world, BlockPos pos, int y, Map<Block, Integer> sampledBlocks) {
        if (y < 16 && sampledBlocks.containsKey(Blocks.STONE)) {
            return "Great for mining. Bring torches and food.";
        }
        String biome = getBiomeDisplayName(world, pos).toLowerCase(Locale.ROOT);
        if (biome.contains("desert")) return "Nights can be dangerous. Build a shelter.";
        if (biome.contains("ocean"))  return "Good for fishing and ocean monuments.";
        if (biome.contains("forest")) return "Excellent for wood. Watch for mobs at night.";
        if (biome.contains("mountain")) return "Wide view; consider towers or a cliff base.";
        if (biome.contains("plains")) return "Ideal for large builds and farms.";
        return "Looks good for exploration and building.";
    }

    private boolean isChunkLoaded(ServerWorld world, int blockX, int blockZ) {
        ChunkPos cp = new ChunkPos(blockX >> 4, blockZ >> 4);
        return world.isChunkLoaded(cp.x, cp.z);
    }

    private String normalizeType(String t) {
        if (isBlank(t)) return null;
        String s = t.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "ore", "矿物" -> "ore";
            case "wood", "木材" -> "wood";
            case "water", "水", "水源" -> "water";
            case "lava", "岩浆" -> "lava";
            case "village", "村庄" -> "village";
            default -> null;
        };
    }

    private Set<Block> oreBlocks() {
        return Set.of(
                Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
                Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS
        );
    }

    private String idPath(Identifier id) {
        return id == null ? "unknown" : id.getPath();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private int clamp(Integer v, int defVal, int min, int max) {
        if (v == null) return defVal;
        return Math.max(min, Math.min(max, v));
    }

    // ============================================================
    // Player lookup
    // ============================================================
    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        try {
            UUID u = UUID.fromString(nameOrUuid);
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }
}