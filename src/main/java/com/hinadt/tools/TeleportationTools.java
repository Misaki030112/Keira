package com.hinadt.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hinadt.AusukaAiMod;
import com.hinadt.ai.AiRuntime;
import com.hinadt.observability.RequestContext;
import com.hinadt.persistence.record.LocationRecord;
import com.hinadt.util.MainThread;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class TeleportationTools {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;

    public TeleportationTools(MinecraftServer server) {
        this.server = server;
    }

    @Tool(
            name = "teleport_player",
            description = """
        Teleport a player safely to a target location with robust parsing and world safety checks.

        INPUT
          - playerName: online player name or UUID
          - destination: preferred order:
              1) saved location name (AI/your memory system), e.g., "home", "farm"
              2) coordinates: "x y z" | "x,y,z" | "x/y/z" (floats allowed)
                 also supports relative "~ ~ ~" and local "^ ^ ^" (relative to player's facing)
              3) another online player's name
              4) natural phrases: "underground", "sky", "spawn", "bed", "sea", "desert", "forest", etc.
          - world: optional dimension hint:
              "overworld" | "nether" | "end" (aliases accepted) or full id like "minecraft:the_nether".
              If omitted, inferred from context.

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "ERR_PLAYER_NOT_FOUND" | "ERR_DEST_UNRESOLVED" | "ERR_WORLD_UNAVAILABLE" | "ERR_TELEPORT",
            "message": "...",
            "from": {"world":"minecraft:overworld","x":..., "y":..., "z":...},
            "to": {"world":"minecraft:the_nether","x":..., "y":..., "z":..., "type":"memory|coordinates|player|semantic|spawn|bed"},
            "method": "memory|coordinates|player|semantic|spawn|bed",
            "crossDimension": true/false,
            "adjustments": {"clampedY":true/false,"movedToSurface":true/false,"collisionFixed":true/false}
          }

        NOTES
          - Uses world height limits dynamically; not hardcoded.
          - Finds a safe landing spot (surface/top of solid, two-block headroom, avoids fluids).
          - All world mutations run on the server main thread.
        """
    )
    public String teleportPlayer(
            @ToolParam(description = "Online player name or UUID") String playerName,
            @ToolParam(description = "Target: saved name / coords / other player / phrase") String destination,
            @ToolParam(description = "Dimension hint: overworld|nether|end or full id (optional)") String worldHint
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] args player='{}' dest='{}' worldHint='{}'",
                RequestContext.midTag(), playerName, destination, worldHint);

        final ServerPlayerEntity player = findPlayer(playerName);
        if (player == null) {
            AusukaAiMod.LOGGER.debug("{} [tool:teleport_player] ERR_PLAYER_NOT_FOUND player='{}'",
                    RequestContext.midTag(), playerName);
            return jsonError("ERR_PLAYER_NOT_FOUND", "Player not found: " + playerName);
        }

        final JsonObject from = posJson(player.getWorld(), player.getX(), player.getY(), player.getZ());

        // 1) saved/memory
        final Target tMem = resolveFromMemory(player, destination);
        if (tMem != null) {
            AusukaAiMod.LOGGER.debug("{} [teleport:resolve] method=memory world='{}' pos=({}, {}, {})",
                    RequestContext.midTag(), tMem.world.getRegistryKey().getValue(), tMem.pos.x, tMem.pos.y, tMem.pos.z);
            return doTeleport(player, from, preferWorld(tMem.world, worldHint, player.getWorld()), tMem, "memory");
        }

        // 2) coordinates
        final Target tCoord = resolveFromCoordinates(player, destination);
        if (tCoord != null) {
            AusukaAiMod.LOGGER.debug("{} [teleport:resolve] method=coordinates world='{}' pos=({}, {}, {})",
                    RequestContext.midTag(), tCoord.world.getRegistryKey().getValue(), tCoord.pos.x, tCoord.pos.y, tCoord.pos.z);
            return doTeleport(player, from, preferWorld(tCoord.world, worldHint, player.getWorld()), tCoord, "coordinates");
        }

        // 3) other player
        final Target tOther = resolveFromOtherPlayer(destination);
        if (tOther != null) {
            AusukaAiMod.LOGGER.debug("{} [teleport:resolve] method=player target='{}' world='{}' pos=({}, {}, {})",
                    RequestContext.midTag(), destination, tOther.world.getRegistryKey().getValue(), tOther.pos.x, tOther.pos.y, tOther.pos.z);
            return doTeleport(player, from, preferWorld(tOther.world, worldHint, player.getWorld()), tOther, "player");
        }

        // 4) semantics (includes bed/spawn if available)
        final Target tSem = resolveFromSemantics(player, destination, worldHint);
        if (tSem != null) {
            AusukaAiMod.LOGGER.debug("{} [teleport:resolve] method=semantic world='{}' pos=({}, {}, {})",
                    RequestContext.midTag(), tSem.world.getRegistryKey().getValue(), tSem.pos.x, tSem.pos.y, tSem.pos.z);
            return doTeleport(player, from, preferWorld(tSem.world, worldHint, player.getWorld()), tSem, "semantic");
        }

        // explicit spawn keyword as fallback
        if (containsAny(destination, "spawn", "出生")) {
            final ServerWorld targetWorld = preferWorld(null, worldHint, player.getWorld());
            if (targetWorld == null) return jsonError("ERR_WORLD_UNAVAILABLE", "Cannot resolve target world.");
            final BlockPos sp = targetWorld.getSpawnPos();
            final Target t = new Target(targetWorld, new Vec3d(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5), "spawn");
            return doTeleport(player, from, targetWorld, t, "spawn");
        }

        AusukaAiMod.LOGGER.debug("{} [teleport:resolve] ERR_DEST_UNRESOLVED dest='{}' worldHint='{}'",
                RequestContext.midTag(), destination, worldHint);

        return jsonError("ERR_DEST_UNRESOLVED",
                "Cannot resolve destination from input. Provide a saved location name, coordinates, another player, or a clearer phrase.");
    }

    /* ---------- world height helpers ---------- */

    private static int minY(ServerWorld w) { return w.getBottomY(); }
    private static int maxY(ServerWorld w) { return w.getBottomY() + w.getHeight() - 1; }

    /* ---------- player finding ---------- */

    private ServerPlayerEntity findPlayer(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        ServerPlayerEntity byName = server.getPlayerManager().getPlayer(nameOrUuid);
        if (byName != null) return byName;
        try {
            UUID u = UUID.fromString(nameOrUuid.trim());
            return server.getPlayerManager().getPlayer(u);
        } catch (Exception ignore) {
            return null;
        }
    }

    /* ---------- destination resolvers ---------- */

    private Target resolveFromOtherPlayer(String input) {
        if (input == null || input.isBlank()) return null;
        ServerPlayerEntity other = findPlayer(input.trim());
        if (other == null) return null;
        return new Target(other.getWorld(), other.getPos(), "player");
    }

    private Target resolveFromMemory(ServerPlayerEntity player, String destination) {
        if (player == null || destination == null || destination.isBlank()) return null;
        try {
            var mem = AiRuntime.getConversationMemory();
            if (mem == null) return null;

            String who = player.getGameProfile().getName();
            LocationRecord rec = mem.getLocationForTeleport(who, destination);
            if (rec == null) return null;

            ServerWorld w = worldFromAny(rec.world());
            if (w == null) w = player.getWorld();
            return new Target(w, new Vec3d(rec.x(), rec.y(), rec.z()), "memory");
        } catch (Throwable t) {
            return null;
        }
    }

    private Target resolveFromCoordinates(ServerPlayerEntity player, String s) {
        if (s == null) return null;
        String in = s.trim();
        String[] raw = in.split("[\\s,/]+");
        if (raw.length != 3) return null;

        // local ^ ^ ^ (relative to facing)
        if (raw[0].startsWith("^") || raw[1].startsWith("^") || raw[2].startsWith("^")) {
            double lx = parseRel(raw[0], '^');
            double ly = parseRel(raw[1], '^');
            double lz = parseRel(raw[2], '^');
            if (Double.isNaN(lx) || Double.isNaN(ly) || Double.isNaN(lz)) return null;
            Vec3d dest = localToWorldOffset(player, lx, ly, lz).add(player.getPos());
            return new Target(player.getWorld(), dest, "coordinates");
        }

        // relative ~ ~ ~ and absolute floats
        double x = parseCoord(raw[0], player.getX(), '~');
        double y = parseCoord(raw[1], player.getY(), '~');
        double z = parseCoord(raw[2], player.getZ(), '~');
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) return null;
        return new Target(player.getWorld(), new Vec3d(x, y, z), "coordinates");
    }

    private Target resolveFromSemantics(ServerPlayerEntity player, String destination, String worldHint) {
        if (player == null || destination == null || destination.isBlank()) return null;

        String s = destination.trim().toLowerCase(Locale.ROOT);

        // world selection: hint > keywords > player's world
        ServerWorld world = preferWorld(null, worldHint, null);
        if (world == null) {
            if (containsAny(s, "overworld", "主世界", "地上")) world = server.getWorld(World.OVERWORLD);
            else if (containsAny(s, "nether", "下界", "地狱")) world = server.getWorld(World.NETHER);
            else if (containsAny(s, "end", "末地", "末路之地")) world = server.getWorld(World.END);
        }
        if (world == null) world = player.getWorld();

        final int bottomY = world.getBottomY();
        final int topY = bottomY + world.getHeight() - 1;
        var base = player.getBlockPos();
        double x = base.getX() + 0.5;
        double y = player.getY();
        double z = base.getZ() + 0.5;

        // bed (best-effort via reflection; may be unavailable on your mappings)
        if (containsAny(s, "bed", "床", "重生点")) {
            Target bed = tryResolveBed(player);
            if (bed != null) return bed;
            // fall through if not available
        }

        // spawn
        if (containsAny(s, "spawn", "出生")) {
            var sp = world.getSpawnPos();
            return new Target(world, new Vec3d(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5), "spawn");
        }

        // vertical intents
        if (containsAny(s, "underground", "地下", "洞")) {
            y = Math.max(bottomY + 5, Math.min(player.getY() - 30, topY - 5));
            return new Target(world, new Vec3d(x, y, z), "semantic");
        }
        if (containsAny(s, "sky", "天空", "高")) {
            y = Math.min(topY - 5, player.getY() + 50);
            return new Target(world, new Vec3d(x, y, z), "semantic");
        }

        // water/sea hint — approximate ocean level
        if (containsAny(s, "ocean", "sea", "海", "水边", "海边")) {
            y = Math.max(bottomY, Math.min(62, topY));
            return new Target(world, new Vec3d(x, y, z), "semantic");
        }

        // coarse biome-ish hints — heuristic offsets
        if (containsAny(s, "desert", "沙漠")) {
            return new Target(world, new Vec3d(x + 500, clampY(y, bottomY, topY), z + 300), "semantic");
        }
        if (containsAny(s, "forest", "森林")) {
            return new Target(world, new Vec3d(x - 300, clampY(y, bottomY, topY), z - 200), "semantic");
        }
        if (containsAny(s, "snow", "冰", "雪")) {
            return new Target(world, new Vec3d(x, clampY(90, bottomY, topY), z - 400), "semantic");
        }
        if (containsAny(s, "mountain", "hill", "山")) {
            return new Target(world, new Vec3d(x + 120, clampY(140, bottomY, topY), z + 120), "semantic");
        }

        // distance hints
        if (containsAny(s, "near", "附近")) {
            return new Target(world, new Vec3d(x + 50, y, z + 50), "semantic");
        }
        if (containsAny(s, "far", "远方")) {
            return new Target(world, new Vec3d(x + 1000, y, z + 1000), "semantic");
        }

        return null;
    }

    /* ---------- safe landing ---------- */

    private SafeAdjust findSafeLanding(ServerWorld world, Vec3d wanted) {
        double y = Math.max(minY(world), Math.min(maxY(world), wanted.y));
        boolean clamped = (y != wanted.y);

        int x = (int) Math.floor(wanted.x);
        int z = (int) Math.floor(wanted.z);
        int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        Vec3d candidate = new Vec3d(wanted.x, Math.max(y, surfaceY + 1.0), wanted.z);

        Vec3d safe = scanVerticalForSpace(world, candidate, 24);
        boolean movedSurface = (safe.y != wanted.y);
        boolean collisionFixed = !safe.equals(candidate);
        return new SafeAdjust(safe, clamped, movedSurface, collisionFixed);
    }

    private Vec3d scanVerticalForSpace(ServerWorld w, Vec3d start, int range) {
        for (int dy = 0; dy <= range; dy++) {
            for (int sign : new int[]{+1, -1}) {
                int y = (int) Math.floor(start.y) + dy * sign;
                if (y < minY(w) || y > maxY(w)) continue;
                if (isStandable(w, (int) Math.floor(start.x), y, (int) Math.floor(start.z))) {
                    return new Vec3d(start.x, y, start.z);
                }
            }
        }
        return new Vec3d(start.x, Math.max(minY(w), Math.min(maxY(w), start.y)), start.z);
    }

    private boolean isStandable(ServerWorld w, int x, int y, int z) {
        BlockPos belowPos = new BlockPos(x, y - 1, z);
        BlockPos feetPos = new BlockPos(x, y, z);
        BlockPos headPos = new BlockPos(x, y + 1, z);
        boolean solidBelow = !w.getBlockState(belowPos).getCollisionShape(w, belowPos).isEmpty();
        boolean airFeet = w.getBlockState(feetPos).isAir() && w.getFluidState(feetPos).isEmpty();
        boolean airHead = w.getBlockState(headPos).isAir() && w.getFluidState(headPos).isEmpty();
        return solidBelow && airFeet && airHead;
    }

    /* ---------- teleport execution ---------- */

    private String doTeleport(ServerPlayerEntity player, JsonObject from, ServerWorld world, Target t, String method) {
        if (world == null) return jsonError("ERR_WORLD_UNAVAILABLE", "Target world unavailable.");
        SafeAdjust adj = findSafeLanding(world, t.pos);
        AtomicReference<String> ref = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            try {
                player.teleport(world, adj.safe.x, adj.safe.y, adj.safe.z, java.util.Set.of(), player.getYaw(), player.getPitch(), false);
                JsonObject root = new JsonObject();
                root.addProperty("ok", true);
                root.addProperty("code", "OK");
                root.addProperty("message", "Teleported");
                root.add("from", from);
                root.add("to", posJson(world, adj.safe.x, adj.safe.y, adj.safe.z));
                root.addProperty("method", method);
                root.addProperty("crossDimension", player.getWorld() != world);
                JsonObject a = new JsonObject();
                a.addProperty("clampedY", adj.clampedY);
                a.addProperty("movedToSurface", adj.movedSurface);
                a.addProperty("collisionFixed", adj.collisionFixed);
                root.add("adjustments", a);
                ref.set(GSON.toJson(root));
            } catch (Exception e) {
                ref.set(jsonError("ERR_TELEPORT", "Teleport failed: " + e.getMessage()));
            }
        });
        return ref.get();
    }

    /* ---------- world parsing ---------- */

    private ServerWorld preferWorld(ServerWorld fromTarget, String hint, ServerWorld fallback) {
        if (fromTarget != null) return fromTarget;
        ServerWorld byHint = worldFromAny(hint);

        AusukaAiMod.LOGGER.debug("{} [teleport:world] picked='{}' reason='{}'",
                RequestContext.midTag(),
                ((byHint != null) ? byHint.getRegistryKey().getValue() : "null"),
                byHint != null ? "hint" : "fallback");

        return (byHint != null) ? byHint : fallback;
    }

    private ServerWorld worldFromAny(String s) {
        if (s == null) return null;
        String k = s.trim();
        if (k.isEmpty()) return null;

        // aliases (both English and Chinese)
        String lower = k.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "overworld", "主世界", "地上")) return server.getWorld(World.OVERWORLD);
        if (containsAny(lower, "nether", "下界", "地狱")) return server.getWorld(World.NETHER);
        if (containsAny(lower, "end", "末地", "末路之地")) return server.getWorld(World.END);

        // full identifier like "minecraft:the_nether"
        Identifier id = Identifier.tryParse(k);
        if (id != null) {
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            AusukaAiMod.LOGGER.debug("{} [teleport:worldFromAny] input='{}' match=id->'{}'",
                    RequestContext.midTag(), s, key.getValue());

            return server.getWorld(key);
        }

        AusukaAiMod.LOGGER.debug("{} [teleport:worldFromAny] no-match input='{}'",
                RequestContext.midTag(), s);
        return null;
    }

    private static boolean containsAny(String hay, String... needles) {
        if (hay == null) return false;
        String l = hay.toLowerCase(Locale.ROOT);
        for (String n : needles) if (l.contains(n.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    /* ---------- bed (best-effort via reflection to be mapping-proof) ---------- */

    private Target tryResolveBed(ServerPlayerEntity player) {
        try {
            // Try methods commonly present in multiple Yarn versions:
            // PlayerEntity#getSpawnPointPosition() : BlockPos
            // PlayerEntity#getSpawnPointDimension(): RegistryKey<World>
            var mPos = PlayerEntity.class.getMethod("getSpawnPointPosition");
            var mDim = PlayerEntity.class.getMethod("getSpawnPointDimension");
            Object posObj = mPos.invoke(player);
            Object dimObj = mDim.invoke(player);
            if (posObj instanceof BlockPos && dimObj instanceof RegistryKey) {
                @SuppressWarnings("unchecked")
                RegistryKey<World> key = (RegistryKey<World>) dimObj;
                ServerWorld w = server.getWorld(key);
                if (w != null) {
                    BlockPos bp = (BlockPos) posObj;
                    return new Target(w, new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5), "bed");
                }
            }
        } catch (Throwable ignore) {
            // Mapping may not expose those getters; fall through
        }
        return null;
    }

    /* ---------- math helpers ---------- */

    private double parseCoord(String tok, double base, char rel) {
        tok = tok.trim();
        if (tok.isEmpty()) return Double.NaN;
        if (tok.charAt(0) == rel) {
            if (tok.length() == 1) return base; // "~"
            try { return base + Double.parseDouble(tok.substring(1)); }
            catch (Exception e) { return Double.NaN; }
        }
        try { return Double.parseDouble(tok); } catch (Exception e) { return Double.NaN; }
    }

    private double parseRel(String tok, char mark) {
        tok = tok.trim();
        if (tok.isEmpty() || tok.charAt(0) != mark) return Double.NaN;
        if (tok.length() == 1) return 0.0;
        try { return Double.parseDouble(tok.substring(1)); } catch (Exception e) { return Double.NaN; }
    }

    private Vec3d localToWorldOffset(ServerPlayerEntity p, double lx, double ly, double lz) {
        double yaw = Math.toRadians(p.getYaw());
        double pitch = Math.toRadians(p.getPitch());
        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d left = up.crossProduct(forward).normalize();
        return left.multiply(lx).add(up.multiply(ly)).add(forward.multiply(lz));
    }

    private static double clampY(double y, int bottomY, int topY) {
        return Math.max(bottomY, Math.min(topY, y));
    }

    /* ---------- DTOs & JSON ---------- */

    private static final class Target {
        final ServerWorld world; final Vec3d pos; final String type;
        Target(ServerWorld w, Vec3d p, String type) { this.world = w; this.pos = p; this.type = type; }
    }

    private static final class SafeAdjust {
        final Vec3d safe; final boolean clampedY, movedSurface, collisionFixed;
        SafeAdjust(Vec3d s, boolean c1, boolean c2, boolean c3) { safe = s; clampedY = c1; movedSurface = c2; collisionFixed = c3; }
    }

    private JsonObject posJson(ServerWorld w, double x, double y, double z) {
        JsonObject o = new JsonObject();
        o.addProperty("world", w.getRegistryKey().getValue().toString());
        o.addProperty("x", Math.round(x * 100.0) / 100.0);
        o.addProperty("y", Math.round(y * 100.0) / 100.0);
        o.addProperty("z", Math.round(z * 100.0) / 100.0);
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
