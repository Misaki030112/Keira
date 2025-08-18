package com.hinadt.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hinadt.AusukaAiMod;
import com.hinadt.observability.RequestContext;
import com.hinadt.util.MainThread;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("resource")
public class WeatherTools {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MinecraftServer server;

    public WeatherTools(MinecraftServer server) { this.server = server; }

    /* ================= change_weather ================= */

    @Tool(
            name = "change_weather",
            description = """
        Change the weather in a given world with safety checks.

        INPUT
          - weatherType: clear | rain | thunder (synonyms accepted; case-insensitive)
          - duration: duration as seconds or human format (e.g. "600", "10m", "90s", "2h30m"); default 10m
          - world: optional. "overworld" | "nether" | "end" or full id "minecraft:the_nether".
          - freezeCycle: optional boolean. If true, disables weather cycle; if false, enables it; if null, leave unchanged.

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "ERR_BAD_TYPE" | "ERR_WORLD_UNAVAILABLE" | "NO_WEATHER_IN_DIMENSION" | "ERR_APPLY",
            "message": "...",
            "world": "minecraft:overworld",
            "weather": "clear|rain|thunder",
            "durationSeconds": 600,
            "freezeCycleApplied": true/false/null
          }
        """
    )
    public String changeWeather(
            @ToolParam(description = "clear/rain/thunder or synonyms") String weatherType,
            @ToolParam(description = "duration (e.g. 600, 10m, 90s, 2h30m)") String duration,
            @ToolParam(description = "world alias or id (optional)") String world,
            @ToolParam(description = "freeze weather cycle? true/false (optional)") Boolean freezeCycle
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:change_weather] args type='{}' duration='{}' world='{}' freezeCycle={}",
                RequestContext.midTag(), weatherType, duration, world, freezeCycle);

        final String kind = normalizeWeather(weatherType);
        if (kind == null) return jsonError("ERR_BAD_TYPE", "Unsupported weather type: " + weatherType);

        final int seconds = parseDurationSeconds(duration, 600); // default 10m
        final ServerWorld w = worldFromAny(world);
        final ServerWorld target = (w != null) ? w : server.getOverworld();
        if (target == null) return jsonError("ERR_WORLD_UNAVAILABLE", "Target world unavailable.");

        final String worldId = target.getRegistryKey().getValue().toString();
        AusukaAiMod.LOGGER.debug("{} [change_weather] normalized type='{}' seconds={} world='{}'",
                RequestContext.midTag(), kind, seconds, worldId);

        // weather not supported in nether/end
        if (!supportsWeather(target)) {
            AusukaAiMod.LOGGER.debug("{} [change_weather] dimension has no weather world='{}'", RequestContext.midTag(), worldId);
            return jsonError("NO_WEATHER_IN_DIMENSION", "This dimension has no rain/thunder: " + worldId);
        }

        AtomicReference<String> out = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            try {
                final int clearTicks  = kind.equals("clear")   ? seconds * 20 : 0;
                final int rainTicks   = (kind.equals("rain") || kind.equals("thunder")) ? seconds * 20 : 0;
                final boolean raining = !kind.equals("clear");
                final boolean thunder = kind.equals("thunder");

                target.setWeather(clearTicks, rainTicks, raining, thunder);

                Boolean freezeApplied = null;
                if (freezeCycle != null) {
                    target.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(!freezeCycle, server);
                    freezeApplied = freezeCycle;
                }

                JsonObject res = new JsonObject();
                res.addProperty("ok", true);
                res.addProperty("code", "OK");
                res.addProperty("message", "Weather changed");
                res.addProperty("world", worldId);
                res.addProperty("weather", kind);
                res.addProperty("durationSeconds", seconds);
                if (freezeCycle != null) res.addProperty("freezeCycleApplied", freezeApplied);
                out.set(GSON.toJson(res));

                AusukaAiMod.LOGGER.debug("{} [change_weather] applied world='{}' weather='{}' sec={} freezeCycle={}",
                        RequestContext.midTag(), worldId, kind, seconds, freezeCycle);

            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("{} [change_weather] ERR_APPLY {}", RequestContext.midTag(), e.toString(), e);
                out.set(jsonError("ERR_APPLY", "Failed to apply weather: " + e.getMessage()));
            }
        });
        return out.get();
    }

    /* ================= set_time ================= */

    @Tool(
            name = "set_time",
            description = """
        Set the time of day in a given world.

        INPUT
          - time: keywords [day=1000, sunrise=0, noon=6000, sunset=12000, night=13000, midnight=18000],
                  or "0..24000", or offset like "+1000"/"-500", or "HH:mm" (24h).
          - world: optional. "overworld" | "nether" | "end" or full id.
          - freezeCycle: optional boolean. If true disables daylight cycle; false enables; null unchanged.

        OUTPUT (JSON)
          {
            "ok": true/false,
            "code": "OK" | "ERR_BAD_TIME" | "ERR_WORLD_UNAVAILABLE" | "ERR_APPLY",
            "message": "...",
            "world": "minecraft:overworld",
            "timeOfDay": 6000,
            "timeKey": "noon",
            "freezeCycleApplied": true/false/null
          }
        """
    )
    public String setTime(
            @ToolParam(description = "time keyword/number/offset/HH:mm") String time,
            @ToolParam(description = "world alias or id (optional)") String world,
            @ToolParam(description = "freeze daylight cycle? true/false (optional)") Boolean freezeCycle
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:set_time] args time='{}' world='{}' freezeCycle={}",
                RequestContext.midTag(), time, world, freezeCycle);

        final ServerWorld w = worldFromAny(world);
        final ServerWorld target = (w != null) ? w : server.getOverworld();
        if (target == null) return jsonError("ERR_WORLD_UNAVAILABLE", "Target world unavailable.");
        final String worldId = target.getRegistryKey().getValue().toString();

        final long current = target.getTimeOfDay() % 24000L;
        final Long parsed = parseTimeValue(time, current);
        if (parsed == null) return jsonError("ERR_BAD_TIME", "Unsupported time format: " + time);

        final long newTod = ((parsed % 24000L) + 24000L) % 24000L; // normalize
        AusukaAiMod.LOGGER.debug("{} [set_time] parsed current={} -> newTod={} world='{}'",
                RequestContext.midTag(), current, newTod, worldId);

        AtomicReference<String> out = new AtomicReference<>();
        MainThread.runSync(server, () -> {
            try {
                target.setTimeOfDay(newTod);
                Boolean freezeApplied = null;
                if (freezeCycle != null) {
                    target.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(!freezeCycle, server);
                    freezeApplied = freezeCycle;
                }

                JsonObject res = new JsonObject();
                res.addProperty("ok", true);
                res.addProperty("code", "OK");
                res.addProperty("message", "Time changed");
                res.addProperty("world", worldId);
                res.addProperty("timeOfDay", newTod);
                res.addProperty("timeKey", timeKey(newTod));
                if (freezeCycle != null) res.addProperty("freezeCycleApplied", freezeApplied);
                out.set(GSON.toJson(res));

                AusukaAiMod.LOGGER.debug("{} [set_time] applied world='{}' timeOfDay={} key='{}' freezeCycle={}",
                        RequestContext.midTag(), worldId, newTod, timeKey(newTod), freezeCycle);

                // 也可选择给该世界在线玩家发一个系统提示
                // target.getPlayers().forEach(p -> p.sendMessageToClient(Text.of("[AI] Time set to " + timeKey(newTod)), false));

            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("{} [set_time] ERR_APPLY {}", RequestContext.midTag(), e.toString(), e);
                out.set(jsonError("ERR_APPLY", "Failed to set time: " + e.getMessage()));
            }
        });
        return out.get();
    }


    @Tool(
            name = "get_world_state",
            description = """
    Get the current weather/time state of a world.

    INPUT
      - world: optional. "overworld" | "nether" | "end" or full id "minecraft:the_nether".
               If omitted, defaults to the Overworld.

    OUTPUT (JSON)
      {
        "ok": true,
        "code": "OK",
        "world": "minecraft:overworld",
        "supportsWeather": true,
        "isRaining": false,
        "isThundering": false,
        "rainGradient": 0.0,
        "thunderGradient": 0.0,
        "clearWeatherTimeTicks": 0,     // present if retrievable
        "rainTimeTicks": 0,             // present if retrievable
        "thunderTimeTicks": 0,          // present if retrievable
        "timeOfDay": 6000,
        "timeKey": "noon",
        "totalTime": 1234567,
        "day": 51,
        "moonPhase": 3,                 // 0..7
        "daylightCycle": true,
        "weatherCycle": true,
        "players": 2,
        "difficulty": "normal"
      }
    """
    )
    public String getWorldState(
            @ToolParam(description = "world alias or id (optional)") String world
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:get_world_state] args world='{}'",
                RequestContext.midTag(), world);

        final ServerWorld w = worldFromAny(world);
        final ServerWorld target = (w != null) ? w : server.getOverworld();
        if (target == null) return jsonError("ERR_WORLD_UNAVAILABLE", "Target world unavailable.");

        final String worldId = target.getRegistryKey().getValue().toString();

        java.util.concurrent.atomic.AtomicReference<String> out = new java.util.concurrent.atomic.AtomicReference<>();
        MainThread.runSync(server, () -> {
            try {
                boolean supports = supportsWeather(target);
                boolean raining = target.isRaining();
                boolean thundering = target.isThundering();
                float rainGrad = target.getRainGradient(1.0f);
                float thunderGrad = target.getThunderGradient(1.0f);

                long timeOfDay = target.getTimeOfDay() % 24000L;   // 0..23999
                long totalTime = target.getTime();                  // total ticks since world start
                long day = totalTime / 24000L;
                int moonPhase = (int)((totalTime / 24000L) % 8L);

                boolean daylightCycle = target.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE);
                boolean weatherCycle  = target.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_WEATHER_CYCLE);

                int players = target.getPlayers().size();
                String difficulty = target.getDifficulty().getName();

                com.google.gson.JsonObject res = new com.google.gson.JsonObject();
                res.addProperty("ok", true);
                res.addProperty("code", "OK");
                res.addProperty("world", worldId);
                res.addProperty("supportsWeather", supports);
                res.addProperty("isRaining", raining);
                res.addProperty("isThundering", thundering);
                res.addProperty("rainGradient", round3(rainGrad));
                res.addProperty("thunderGradient", round3(thunderGrad));

                // Best-effort: read remaining weather times via reflection (mapping-safe).
                addWeatherTimesIfAvailable(res, target);

                res.addProperty("timeOfDay", timeOfDay);
                res.addProperty("timeKey", timeKey(timeOfDay));
                res.addProperty("totalTime", totalTime);
                res.addProperty("day", day);
                res.addProperty("moonPhase", moonPhase);
                res.addProperty("daylightCycle", daylightCycle);
                res.addProperty("weatherCycle", weatherCycle);
                res.addProperty("players", players);
                res.addProperty("difficulty", difficulty);

                out.set(GSON.toJson(res));

                AusukaAiMod.LOGGER.debug("{} [tool:get_world_state] world='{}' raining={} thundering={} tod={} key='{}'",
                        RequestContext.midTag(), worldId, raining, thundering, timeOfDay, timeKey(timeOfDay));
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("{} [tool:get_world_state] error {}", RequestContext.midTag(), e.toString(), e);
                out.set(jsonError("ERR_GET_STATE", "Failed to get world state: " + e.getMessage()));
            }
        });
        return out.get();
    }





    /* ================= helpers ================= */

    private boolean supportsWeather(ServerWorld w) {
        var key = w.getRegistryKey();
        return key == World.OVERWORLD; // vanilla: only overworld has rain/thunder
    }

    /** normalize clear/rain/thunder and common synonyms (en/zh) */
    private String normalizeWeather(String s) {
        if (s == null) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return null;
        switch (k) {
            case "clear": case "sunny": case "晴": case "晴天": return "clear";
            case "rain": case "rainy": case "雨": case "下雨": case "雨天": return "rain";
            case "thunder": case "thunderstorm": case "storm": case "雷": case "雷雨": case "雷暴": return "thunder";
            default: return null;
        }
    }

    /** parse "600", "10m", "90s", "2h30m" → seconds; defaultSec used if null/blank */
    private int parseDurationSeconds(String s, int defaultSec) {
        if (s == null || s.isBlank()) return defaultSec;
        String in = s.trim().toLowerCase(Locale.ROOT);
        if (in.matches("^\\d+$")) return clampSeconds(Integer.parseInt(in));
        Pattern p = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
        Matcher m = p.matcher(in);
        if (m.matches()) {
            int h = (m.group(1) != null) ? Integer.parseInt(m.group(1)) : 0;
            int mnt = (m.group(2) != null) ? Integer.parseInt(m.group(2)) : 0;
            int sec = (m.group(3) != null) ? Integer.parseInt(m.group(3)) : 0;
            return clampSeconds(h * 3600 + mnt * 60 + sec);
        }
        // also accept "5min"
        if (in.endsWith("min")) {
            String num = in.substring(0, in.length() - 3).trim();
            if (num.matches("^\\d+$")) return clampSeconds(Integer.parseInt(num) * 60);
        }
        return defaultSec;
    }

    private int clampSeconds(int s) { return Math.max(1, Math.min(24 * 3600, s)); }

    /** parse time to time-of-day [0..23999]; support keywords, absolute, offsets, and HH:mm */
    private Long parseTimeValue(String s, long current) {
        if (s == null || s.isBlank()) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        switch (k) {
            case "sunrise": case "dawn": case "黎明": return 0L;
            case "day": case "白天": case "早上": case "morning": return 1000L;
            case "noon": case "正午": case "中午": return 6000L;
            case "sunset": case "黄昏": case "傍晚": return 12000L;
            case "night": case "夜晚": case "晚上": case "evening": return 13000L;
            case "midnight": case "午夜": case "深夜": return 18000L;
        }
        if (k.startsWith("+") || k.startsWith("-")) {
            try { long d = Long.parseLong(k); return (current + d) % 24000L; } catch (Exception ignore) {}
        }
        if (k.matches("^\\d{1,5}$")) {
            long v = Long.parseLong(k);
            if (v >= 0 && v < 24000) return v;
        }
        if (k.matches("^\\d{1,2}:\\d{2}$")) {
            String[] parts = k.split(":");
            int hh = Integer.parseInt(parts[0]);
            int mm = Integer.parseInt(parts[1]);
            // Simple linear mapping: 00:00 -> 18000? Here we use: 00:00 -> 18000 (midnight), 12:00 -> 6000 (noon)
            // But for intuitiveness, here we use a daytime proportional mapping: 00:00 -> 18000, 06:00 -> 0, 12:00 -> 6000, 18:00 -> 12000
            int totalMin = (hh % 24) * 60 + Math.min(59, Math.max(0, mm));
            double ratio = totalMin / (24.0 * 60.0);
            long tod = (long) Math.floor(ratio * 24000.0);
            return tod;
        }
        return null;
    }

    private String timeKey(long t) {
        if (t < 1000) return "sunrise";
        if (t < 6000) return "day";
        if (t < 12000) return "noon";
        if (t < 13000) return "sunset";
        if (t < 18000) return "night";
        return "midnight";
    }

    // Round to 3 decimals for gradients
    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    /** Try to read clear/rain/thunder remaining ticks via reflection (mapping-friendly). */
    private void addWeatherTimesIfAvailable(com.google.gson.JsonObject res, ServerWorld world) {
        try {
            Object props = world.getLevelProperties(); // type varies across mappings
            Integer clear = tryCallInt(props, "getClearWeatherTime");
            Integer rain = tryCallInt(props, "getRainTime");
            Integer thunder = tryCallInt(props, "getThunderTime");
            if (clear != null)   res.addProperty("clearWeatherTimeTicks", clear);
            if (rain != null)    res.addProperty("rainTimeTicks", rain);
            if (thunder != null) res.addProperty("thunderTimeTicks", thunder);
            AusukaAiMod.LOGGER.debug("{} [world_state] weatherTimes clear={} rain={} thunder={}",
                    RequestContext.midTag(), clear, rain, thunder);
        } catch (Throwable ignore) {
            // silently skip if not available in this mapping
        }
    }

    private Integer tryCallInt(Object obj, String method) {
        try {
            java.lang.reflect.Method m = obj.getClass().getMethod(method);
            Object v = m.invoke(obj);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {}
        return null;
    }



    /* world helpers */

    private ServerWorld worldFromAny(String s) {
        if (s == null || s.isBlank()) return null;
        String k = s.trim().toLowerCase(Locale.ROOT);
        if (k.contains("overworld") || k.contains("主世界") || k.contains("地上")) return server.getWorld(World.OVERWORLD);
        if (k.contains("nether") || k.contains("下界") || k.contains("地狱")) return server.getWorld(World.NETHER);
        if (k.contains("end") || k.contains("末地") || k.contains("末路之地")) return server.getWorld(World.END);
        Identifier id = Identifier.tryParse(s.trim());
        if (id != null) {
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            return server.getWorld(key);
        }
        return null;
    }

    /* JSON helpers */

    private String jsonError(String code, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("code", code);
        o.addProperty("message", message);
        return GSON.toJson(o);
    }
}
