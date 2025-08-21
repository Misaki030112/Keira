package com.keira.ai.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class PlayerContextBuilder {

    /**
     * Build an English, machine-friendly JSON context describing the player's current state.
     * This is intended for AI consumption: stable keys, minimal prose, no Markdown.
     */
    public String build(ServerPlayerEntity player) {
        final ServerWorld world = player.getWorld();
        final BlockPos pos = player.getBlockPos();

        // ---- core identifiers ----
        final String name = player.getGameProfile().getName();
        final String uuid = player.getUuidAsString();
        final String gameMode = player.interactionManager.getGameMode().asString();

        // ---- health / hunger / armor / xp ----
        final float health = player.getHealth();
        final float maxHealth = player.getMaxHealth();
        final int food = player.getHungerManager().getFoodLevel();
        final float saturation = player.getHungerManager().getSaturationLevel();
        final int armor = player.getArmor();
        final int xpLevel = player.experienceLevel;

        // ---- world / dimension / biome ----
        final String dimensionId = world.getRegistryKey().getValue().toString(); // e.g., "minecraft:overworld"
        final Identifier biomeId = world.getBiome(pos).getKey().map(RegistryKey::getValue).orElse(null);
        final String biome = (biomeId == null) ? "unknown" : biomeId.toString();

        // ---- position / facing ----
        final int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
        final float yaw = player.getYaw();
        final float pitch = player.getPitch();

        // ---- time / weather ----
        final long dayTicks = world.getTimeOfDay() % 24000L;
        final boolean isNight = dayTicks >= 13000L && dayTicks < 23000L;
        final boolean thundering = world.isThundering();
        final boolean raining = world.isRaining(); // Overworld only effectively changes visuals; Nether/End: false
        final String weather = thundering ? "thunder" : (raining ? "rain" : "clear");

        // ---- light & coarse safety ----
        final int light = world.getLightLevel(pos); // block+sky merged; quick proxy for mob spawn risk
        final var key = world.getRegistryKey();
        final String dimensionSafety = getDimensionSafety(key);
        final String threat = estimateThreat(dimensionSafety, isNight, light);

        // ---- held items ----
        final JsonObject mainHand = stackSummary(player.getMainHandStack());
        final JsonObject offHand  = stackSummary(player.getOffHandStack());

        // ---- status effects ----
        final JsonArray effects = new JsonArray();
        for (StatusEffectInstance e : player.getStatusEffects()) {
            final JsonObject je = new JsonObject();
            var entry = e.getEffectType();
            Identifier effId = entry.getKey().map(RegistryKey::getValue).orElse(null);

            je.addProperty("id", effId == null ? "unknown" : effId.toString());
            je.addProperty("amplifier", e.getAmplifier());
            je.addProperty("duration_ticks", e.getDuration());
            effects.add(je);
        }

        // ---- compose JSON ----
        final JsonObject root = new JsonObject();

        final JsonObject identity = new JsonObject();
        identity.addProperty("name", name);
        identity.addProperty("uuid", uuid);
        identity.addProperty("gamemode", gameMode);
        root.add("player", identity);

        final JsonObject vitals = new JsonObject();
        vitals.addProperty("health", round1(health));
        vitals.addProperty("maxHealth", round1(maxHealth));
        vitals.addProperty("food", food);
        vitals.addProperty("saturation", round1(saturation));
        vitals.addProperty("armor", armor);
        vitals.addProperty("xpLevel", xpLevel);
        root.add("vitals", vitals);

        final JsonObject env = new JsonObject();
        env.addProperty("dimension", dimensionId);
        env.addProperty("biome", biome);
        env.addProperty("timeOfDayTicks", dayTicks);
        env.addProperty("isNight", isNight);
        env.addProperty("weather", weather);
        env.addProperty("light", light);
        env.addProperty("dimensionSafety", dimensionSafety);
        env.addProperty("threat", threat); // "low" | "medium" | "high"
        root.add("environment", env);

        final JsonObject position = new JsonObject();
        position.addProperty("x", bx);
        position.addProperty("y", by);
        position.addProperty("z", bz);
        position.addProperty("yaw", round1(yaw));
        position.addProperty("pitch", round1(pitch));
        root.add("position", position);

        final JsonObject equipment = new JsonObject();
        equipment.add("mainHand", mainHand);
        equipment.add("offHand", offHand);
        root.add("equipment", equipment);

        root.add("effects", effects);

        return root.toString();
    }

    private static @NotNull String getDimensionSafety(RegistryKey<World> key) {
        final String dimensionSafety;
        if (key == World.OVERWORLD) {
            dimensionSafety = "normal";
        } else if (key == World.NETHER) {
            dimensionSafety = "dangerous";
        } else if (key == World.END) {
            dimensionSafety = "extreme";
        } else {
            dimensionSafety = "unknown";
        }
        return dimensionSafety;
    }

    // ---- helpers ----

    /** Coarse threat estimation for AI heuristics. */
    private static String estimateThreat(String dimensionSafety, boolean isNight, int light) {
        int score = 0;
        // dimension baseline
        score += switch (dimensionSafety) {
            case "extreme" -> 3; // End
            case "dangerous" -> 2; // Nether
            case "normal" -> 0;
            default -> 1;
        };
        if (isNight) score += 1;
        if (light < 7) score += 1; // hostile mobs spawn at low light
        return (score >= 3) ? "high" : (score >= 1 ? "medium" : "low");
    }

    /** Summarize an ItemStack into id/count; omit NBT for compactness. */
    private static JsonObject stackSummary(net.minecraft.item.ItemStack stack) {
        final JsonObject j = new JsonObject();
        if (stack == null || stack.isEmpty()) {
            j.addProperty("id", "empty");
            j.addProperty("count", 0);
            return j;
        }
        final var item = stack.getItem();
        final Identifier id = Registries.ITEM.getId(item);
        j.addProperty("id", id.toString());
        j.addProperty("count", stack.getCount());
        return j;
    }

    private static float round1(float v) { return Math.round(v * 10f) / 10f; }
}
