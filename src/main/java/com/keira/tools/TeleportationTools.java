package com.keira.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.keira.KeiraAiMod;
import com.keira.observability.RequestContext;
import com.keira.util.MainThread;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TeleportationTools (reworked, no phrase/keyword mapping).
 * <p>
 * - World selection relies on explicit world IDs (worldHint or dimension:<id>), not fuzzy phrases.
 * - Destination supports canonical forms only:
 *   1) Coordinates: "x y z" or "~ ~ ~" (relative to player)
 *   2) Tokens: "bed", "spawn"
 *   3) POI: "poi:village" (nearest villagers within loaded chunks)
 *   4) Dimension: "dimension:minecraft:the_end" (falls back to that world's spawn area)
 * - Safe landing: vertical scan to find standable 2-block space, no chunk generation.
 * - All comments, logs, and tool descriptions are in English.
 * <p>
 * Compatible with Yarn/Fabric 1.21.8 dynamic registries.
 */
public class TeleportationTools {

    private final MinecraftServer server;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public TeleportationTools(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------ Tool ------------------------------
    @Tool(
            name = "teleport_player",
            description = """
            Teleport a player to a canonical destination. No natural-language or keyword matching is performed.
            
            INPUT CONTRACT
            - player: Online player name or UUID (required).
            - dest (required): one of the following canonical forms ONLY:
              1) Coordinates      → "x y z"               e.g. "100 64 -20"
                 Relative coords  → "~ ~1 ~-5"            (relative to player position)
              2) Token            → "bed" | "spawn"
              3) POI              → "poi:village"         (nearest villagers within LOADED chunks only; never generates new chunks)
              4) Dimension        → "dimension:<worldId>" e.g. "dimension:minecraft:the_end"
                 (When dest is a Dimension form, the world is that dimension and position resolves near its world spawn.)
            - worldHint (optional): Explicit world id (e.g. "minecraft:overworld" | "minecraft:the_nether" | "minecraft:the_end").
              Used ONLY when dest is coordinates / token / poi to select the target world. Ignored for dimension-form dest.
            
            WORLD RESOLUTION PRIORITY
            1) If dest starts with "dimension:", use that world (ignores worldHint for world choice).
            2) Else if worldHint is a valid world id, use that world.
            3) Else use the player’s current world.
            
            SAFETY & BEHAVIOR
            - Safe-landing is enforced: the tool vertically scans to find a standable 2-block-high space (feet + head free, solid block below).
            - No new chunk generation: POI queries and scans work on already-loaded area only.
            - If a token implies a different world (e.g., bed in nether) and no matching worldHint is supplied, the call fails with guidance.
            
            RETURNS (JSON)
            {
              "ok": true,
              "code": "OK",
              "message": "Teleported.",
              "world": "minecraft:the_end",
              "x": 0.5, "y": 65.0, "z": 0.5,
              "adjust": { "clampedY": true, "movedSurface": true, "collisionFixed": false },
              "costMs": 12
            }
            
            ERROR CODES
            - ERR_PLAYER_OFFLINE         → Target player is not online
            - ERR_BAD_DEST               → dest is empty or not a canonical form
            - ERR_WORLD_NOT_FOUND        → Provided dimension/world id does not exist
            - ERR_NO_BED                 → No valid bed spawn point for the player
            - ERR_DEST_UNRESOLVED        → Could not resolve a valid target (e.g., bed in another world without worldHint; no loaded village)
            - ERR_TELEPORT               → Unhandled server-side error during teleport
            
            USAGE EXAMPLES
            - To the End dimension (near its world spawn):
              { "player":"Alice", "dest":"dimension:minecraft:the_end" }
            
            - Cross-dimension absolute coordinates (to the Nether):
              { "player":"Bob", "dest":"100 64 0", "worldHint":"minecraft:the_nether" }
            
            - Relative nudge in-place (current world):
              { "player":"Eve", "dest":"~ ~1 ~-5" }
            
            - Nearest village within LOADED chunks:
              { "player":"Eve", "dest":"poi:village" }
            
            - Player bed in Overworld (require matching worldHint if bed is in a different world):
              { "player":"Eve", "dest":"bed", "worldHint":"minecraft:overworld" }
            """
    )
    public String teleportPlayer(
            @ToolParam(description = "Online player name or UUID") String playerNameOrUuid,
            @ToolParam(description = "Canonical dest: <x y z> | ~-coords | bed | spawn | poi:village | dimension:<worldId>") String dest,
            @ToolParam(description = "Explicit world id for coords/token/poi; ignored for dimension:<...>") String worldHint
    ) {
        long t0 = System.nanoTime();
        KeiraAiMod.LOGGER.debug("{} [tool:teleport_player] args player='{}' dest='{}' worldHint='{}'",
                RequestContext.midTag(), playerNameOrUuid, dest, worldHint);

        final AtomicReference<String> out = new AtomicReference<>();

        MainThread.runSync(server, () -> {
            try {
                ServerPlayerEntity player = findOnlinePlayer(playerNameOrUuid);
                if (player == null) {
                    out.set(jsonError("ERR_PLAYER_OFFLINE", "Target player is not online."));
                    return;
                }

                // World selection
                ServerWorld current = player.getWorld();
                WorldChoice wc = resolveWorldFor(dest, worldHint, current);
                if (!wc.ok) { out.set(jsonError(wc.code, wc.message)); return; }
                ServerWorld world = wc.world;
                KeiraAiMod.LOGGER.debug("{} [teleport:world] picked='{}' reason='{}'",
                        RequestContext.midTag(), world.getRegistryKey().getValue(), wc.reason);

                // Destination resolution
                Target target = resolveTarget(player, world, dest);
                if (!target.ok) { out.set(jsonError(target.code, target.message)); return; }
                KeiraAiMod.LOGGER.debug("{} [teleport:resolve] method='{}' world='{}' pos=({},{},{})",
                        RequestContext.midTag(), target.method, world.getRegistryKey().getValue(),
                        round2(target.pos.x), round2(target.pos.y), round2(target.pos.z));

                // Safe landing
                SafeAdjust adj = adjustSafePosition(world, target.pos);

                // Execute
                doTeleport(player, world, adj.safe);

                long ms = (System.nanoTime() - t0) / 1_000_000L;
                JsonObject root = posJson(world, adj.safe.x, adj.safe.y, adj.safe.z);
                root.addProperty("ok", true);
                root.addProperty("code", "OK");
                root.addProperty("message", "Teleported.");
                JsonObject a = new JsonObject();
                a.addProperty("clampedY", adj.clampedY);
                a.addProperty("movedSurface", adj.movedSurface);
                a.addProperty("collisionFixed", adj.collisionFixed);
                a.addProperty("platformBuilt", adj.platformBuilt);
                root.add("adjust", a);
                root.addProperty("costMs", ms);
                out.set(GSON.toJson(root));
            } catch (Exception e) {
                out.set(jsonError("ERR_TELEPORT", "Teleport failed: " + e.getMessage()));
            }
        });

        return out.get();
    }

    // ------------------------------ Resolve world ------------------------------

    private record WorldChoice(boolean ok, String code, String message, ServerWorld world, String reason) {}

    private WorldChoice resolveWorldFor(String dest, String worldHint, ServerWorld fallback) {
        // Case: explicit dimension form in dest
        String d = (dest == null) ? "" : dest.trim();
        if (d.startsWith("dimension:")) {
            String id = d.substring("dimension:".length()).trim();
            ServerWorld w = worldFromId(id);
            if (w == null) return new WorldChoice(false, "ERR_WORLD_NOT_FOUND", "Dimension not found: " + id, null, "");
            return new WorldChoice(true, "OK", "", w, "dimension-in-dest");
        }
        // Else: take hint if present
        ServerWorld hinted = worldFromId(worldHint);
        if (hinted != null && fallback != null && hinted != fallback) {
            return new WorldChoice(true, "OK", "", hinted, "hint");
        }
        // Fallback: player's current world
        return new WorldChoice(true, "OK", "", fallback, "player-world");
    }

    private ServerWorld worldFromId(String any) {
        if (any == null || any.isBlank()) return null;
        Identifier id;
        try { id = Identifier.of(any.trim()); } catch (Exception e) { return null; }
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
        return server.getWorld(key);
    }

    // ------------------------------ Resolve destination ------------------------------

    private record Target(boolean ok, String code, String message, String method, Vec3d pos) {}

    private Target resolveTarget(ServerPlayerEntity player, ServerWorld world, String destRaw) {
        if (destRaw == null || destRaw.isBlank()) {
            return new Target(false, "ERR_BAD_DEST", "Empty destination", "", null);
        }
        String s = destRaw.trim();

        // 1) Coordinates
        Vec3d coord = parseCoordinates(s, player.getPos());
        if (coord != null) {
            return new Target(true, "OK", "", "coordinates", coord);
        }

        // 2) Tokens
        if (s.equalsIgnoreCase("bed")) {
            ServerPlayerEntity.Respawn resp = player.getRespawn();
            if (resp != null) {
                RegistryKey<World> bedDim = resp.dimension();
                ServerWorld bedWorld = server.getWorld(bedDim);
                BlockPos bed = resp.pos();
                if (bed != null && bedWorld != null && bedWorld == world) {
                    return new Target(true, "OK", "", "bed", new Vec3d(bed.getX() + 0.5, bed.getY() + 1.0, bed.getZ() + 0.5));
                } else if (bed != null && bedWorld != null) {
                    // Mismatch: guidance — ask caller to set worldHint or use dimension: form
                    return new Target(false, "ERR_DEST_UNRESOLVED", "Bed is in a different world; pass worldHint or use 'dimension:<id>'", "bed", null);
                }
            }
            return new Target(false, "ERR_NO_BED", "No valid bed spawn point found", "bed", null);
        }
        if (s.equalsIgnoreCase("spawn") || s.equalsIgnoreCase("worldspawn")) {
            BlockPos sp = world.getSpawnPos();
            return new Target(true, "OK", "", "spawn", new Vec3d(sp.getX() + 0.5, sp.getY() + 2.0, sp.getZ() + 0.5));
        }

        // 3) POI
        if (s.equalsIgnoreCase("poi:village")) {
            Vec3d v = locateLoadedVillageLike(world, player.getPos(), 768);
            if (v != null) return new Target(true, "OK", "", "poi:village", v);
            return new Target(false, "ERR_DEST_UNRESOLVED", "No village-like area found in loaded chunks", "poi:village", null);
        }

        // 4) Dimension form handled in world phase; here provide a spawn fallback if caller still passes it
        if (s.startsWith("dimension:")) {
            ServerWorld w = worldFromId(s.substring("dimension:".length()).trim());
            if (w == null) return new Target(false, "ERR_WORLD_NOT_FOUND", "Dimension not found", "dimension", null);
            BlockPos sp = w.getSpawnPos();
            return new Target(true, "OK", "", "dimension-spawn", new Vec3d(sp.getX() + 0.5, sp.getY() + 2.0, sp.getZ() + 0.5));
        }

        return new Target(false, "ERR_DEST_UNRESOLVED", "Cannot resolve destination", "", null);
    }

    private Vec3d parseCoordinates(String s, Vec3d base) {
        String[] parts = s.split("\\s+");
        if (parts.length != 3) return null;
        Double[] vals = new Double[3];
        for (int i = 0; i < 3; i++) {
            String p = parts[i];
            boolean relative = p.startsWith("~");
            String num = p.replace("~", "").trim();
            double v;
            if (num.isEmpty()) v = 0.0;
            else try { v = Double.parseDouble(num); } catch (Exception e) { return null; }
            vals[i] = relative ? ((i==0?base.x:(i==1?base.y:base.z)) + v) : v;
        }
        return new Vec3d(vals[0], vals[1], vals[2]);
    }

    private ServerWorld worldFromKey(RegistryKey<World> key) { return (key == null) ? null : server.getWorld(key); }

    // ------------------------------ POI: loaded "village-like" ------------------------------

    private Vec3d locateLoadedVillageLike(ServerWorld world, Vec3d from, int radius) {
        Box box = new Box(from.x - radius, from.y - 128, from.z - radius,
                          from.x + radius, from.y + 128, from.z + radius);
        List<VillagerEntity> villagers = world.getEntitiesByClass(VillagerEntity.class, box, v -> true);
        if (villagers.isEmpty()) return null;
        VillagerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (VillagerEntity v : villagers) {
            double d = v.squaredDistanceTo(from);
            if (d < best) { best = d; nearest = v; }
        }
        if (nearest == null) return null;
        return nearest.getPos().add(0, 1.0, 0);
    }

    // ------------------------------ Safe landing & teleport ------------------------------

    
    // Horizontal fallback: search for nearest standable column around origin in LOADED chunks (no new chunks generated here).
    private Vec3d findStandableAround(ServerWorld w, BlockPos origin, int startY, int maxRadius, int step) {
        step = Math.max(1, step);
        for (int r = 0; r <= maxRadius; r += step) {
            // scan square ring edges
            for (int dx = -r; dx <= r; dx += step) {
                int x1 = origin.getX() + dx;
                int z1 = origin.getZ() - r;
                if (isChunkLoadedAt(w, x1, z1)) {
                    Vec3d v = scanVerticalForSpace(w, new Vec3d(x1 + 0.5, startY, z1 + 0.5), 256);
                    if (isStandable(w, BlockPos.ofFloored(v.x, v.y, v.z))) return v;
                }
                int z2 = origin.getZ() + r;
                if (isChunkLoadedAt(w, x1, z2)) {
                    Vec3d v = scanVerticalForSpace(w, new Vec3d(x1 + 0.5, startY, z2 + 0.5), 256);
                    if (isStandable(w, BlockPos.ofFloored(v.x, v.y, v.z))) return v;
                }
            }
            for (int dz = -r; dz <= r; dz += step) {
                int z1 = origin.getZ() + dz;
                int x1 = origin.getX() - r;
                if (isChunkLoadedAt(w, x1, z1)) {
                    Vec3d v = scanVerticalForSpace(w, new Vec3d(x1 + 0.5, startY, z1 + 0.5), 256);
                    if (isStandable(w, BlockPos.ofFloored(v.x, v.y, v.z))) return v;
                }
                int x2 = origin.getX() + r;
                if (isChunkLoadedAt(w, x2, z1)) {
                    Vec3d v = scanVerticalForSpace(w, new Vec3d(x2 + 0.5, startY, z1 + 0.5), 256);
                    if (isStandable(w, BlockPos.ofFloored(v.x, v.y, v.z))) return v;
                }
            }
        }
        return null;
    }

    // Check if the chunk containing the given block column is loaded (non-deprecated).
    private static boolean isChunkLoadedAt(ServerWorld world, int blockX, int blockZ) {
        return world.isChunkLoaded(blockX >> 4, blockZ >> 4);
    }

    // As a last resort, build a small platform a few blocks below target and clear space above.
    private boolean buildSafetyPlatform(ServerWorld w, BlockPos center, int y, int radius) {
        try {
            int cx = center.getX();
            int cz = center.getZ();
            // Choose block by dimension
            String key = w.getRegistryKey().getValue().toString();
            var state = key.endsWith("the_end") ? Blocks.END_STONE.getDefaultState()
                      : (key.endsWith("the_nether") ? Blocks.NETHERRACK.getDefaultState()
                                                    : Blocks.STONE.getDefaultState());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    // place platform
                    w.setBlockState(p, state);
                    // clear 2 blocks of air above
                    w.setBlockState(p.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
                    w.setBlockState(p.up(2), net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
            KeiraAiMod.LOGGER.debug("{} [teleport:platform] world='{}' y={} r={}",
                    RequestContext.midTag(), w.getRegistryKey().getValue(), y, radius);
            return true;
        } catch (Exception e) {
            KeiraAiMod.LOGGER.warn("{} [teleport:platform] failed: {}", RequestContext.midTag(), e.getMessage());
            return false;
        }
    }

    private record SafeAdjust(Vec3d safe, boolean clampedY, boolean movedSurface, boolean collisionFixed, boolean platformBuilt) {}

    private SafeAdjust adjustSafePosition(ServerWorld w, Vec3d wanted) {
        double minY = w.getBottomY();
        double maxY = w.getBottomY() + w.getHeight() - 1;
        double y = Math.max(minY + 1, Math.min(maxY, wanted.y));
        boolean clamped = (y != wanted.y);

        Vec3d candidate = new Vec3d(wanted.x, y, wanted.z);

        // (1) snap to heightmap top if the column is loaded
        int ox = (int) Math.floor(candidate.x);
        int oz = (int) Math.floor(candidate.z);
        if (isChunkLoadedAt(w, ox, oz)) {
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, ox, oz);
            if (top > w.getBottomY()) {
                candidate = new Vec3d(ox + 0.5, top + 1.0, oz + 0.5);
            }
        }

        // (2) vertical scan with wider range
        Vec3d safe = scanVerticalForSpace(w, candidate, 192);
        boolean movedSurface = (safe.y != candidate.y);

        boolean platformBuilt = false;

        // (3) horizontal fallback if not standable
        BlockPos pf = BlockPos.ofFloored(safe.x, safe.y, safe.z);
        if (!isStandable(w, pf)) {
            Vec3d around = findStandableAround(
                    w,
                    BlockPos.ofFloored(candidate.x, candidate.y, candidate.z),
                    (int) Math.floor(candidate.y),
                    16,
                    2
            );
            if (around != null) {
                safe = around;
                pf = BlockPos.ofFloored(safe.x, safe.y, safe.z);
            }
        }

        // (4) ultimate fallback: build a small platform a few blocks below and land there
        if (!isStandable(w, pf)) {
            int platY = Math.max((int) Math.floor(wanted.y) - 6, (int) (w.getBottomY() + 4));
            BlockPos center = new BlockPos(ox, platY, oz);
            boolean ok = buildSafetyPlatform(w, center, platY, 1); // 3x3 platform (radius=1)
            if (ok) {
                safe = new Vec3d(center.getX() + 0.5, platY + 1.0, center.getZ() + 0.5);
                platformBuilt = true;
            }
        }

        return new SafeAdjust(safe, clamped, movedSurface, false, platformBuilt);
    
    }

    private Vec3d scanVerticalForSpace(ServerWorld w, Vec3d start, int range) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 0; dy <= range; dy++) {
            for (int sign : new int[]{-1, +1}) {
                int y = (int)Math.floor(start.y + sign * dy);
                int topY = w.getBottomY() + w.getHeight() - 1;
                if (y < w.getBottomY()+1 || y > topY-2) continue;
                m.set(start.x, y, start.z);
                if (isStandable(w, m)) {
                    return new Vec3d(m.getX() + 0.5, y, m.getZ() + 0.5);
                }
            }
        }
        return start;
    }

    private boolean isStandable(ServerWorld w, BlockPos posFeet) {
        BlockPos below = posFeet.down();
        return !w.getBlockState(below).getCollisionShape(w, below).isEmpty()
            && w.getBlockState(posFeet).getCollisionShape(w, posFeet).isEmpty()
            && w.getBlockState(posFeet.up()).getCollisionShape(w, posFeet.up()).isEmpty();
    }

    private void doTeleport(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        if (player.getWorld() != world) {
            player.teleport(world, pos.x, pos.y, pos.z, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch(), false);
        } else {
            player.requestTeleport(pos.x, pos.y, pos.z);
        }
    }

    // ------------------------------ utils ------------------------------

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

    private static double round2(double v) { return Math.round(v*100.0)/100.0; }

    private JsonObject posJson(ServerWorld w, double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("world", w.getRegistryKey().getValue().toString());
        o.addProperty("x", round2(x));
        o.addProperty("y", round2(y));
        o.addProperty("z", round2(z));
        return o;
    }

    private String jsonError(String code, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("code", code);
        o.addProperty("message", msg);
        return GSON.toJson(o);
    }
}
