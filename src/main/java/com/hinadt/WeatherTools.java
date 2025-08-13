package com.hinadt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 天气控制工具
 * 支持晴天、雨天、雷雨等天气控制
 */
public class WeatherTools {
    
    private final MinecraftServer server;
    
    public WeatherTools(MinecraftServer server) {
        this.server = server;
    }
    
    @Tool(
        name = "change_weather",
        description = """
        改变指定世界的天气状况。支持的天气类型：
        - clear/晴天：清除所有降水和雷暴
        - rain/雨天：开始下雨
        - thunder/雷雨：开始雷雨天气
        """
    )
    public String changeWeather(
        @ToolParam(description = "天气类型：clear/晴天、rain/雨天、thunder/雷雨") String weatherType,
        @ToolParam(description = "持续时间（秒），可选，默认600秒（10分钟）") Integer duration,
        @ToolParam(description = "目标世界，可选：overworld(主世界)、nether(下界)、end(末地)，默认主世界") String world
    ) {
        ServerWorld targetWorld = getTargetWorld(world);
        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }
        
        int weatherDuration = (duration != null && duration > 0) ? duration * 20 : 12000; // 转换为游戏tick
        
        AtomicReference<String> result = new AtomicReference<>("天气变更失败");
        
        runOnMainAndWait(() -> {
            try {
                String weatherName = parseWeatherType(weatherType);
                
                switch (weatherName.toLowerCase()) {
                    case "clear":
                    case "晴天":
                        targetWorld.setWeather(weatherDuration, 0, false, false);
                        result.set("☀️ 天气已变更为晴天，持续 " + (weatherDuration/20) + " 秒");
                        break;
                        
                    case "rain":
                    case "雨天":
                        targetWorld.setWeather(0, weatherDuration, true, false);
                        result.set("🌧️ 天气已变更为雨天，持续 " + (weatherDuration/20) + " 秒");
                        break;
                        
                    case "thunder":
                    case "雷雨":
                        targetWorld.setWeather(0, weatherDuration, true, true);
                        result.set("⛈️ 天气已变更为雷雨，持续 " + (weatherDuration/20) + " 秒");
                        break;
                        
                    default:
                        result.set("❌ 未知的天气类型：" + weatherType + "。支持的类型：晴天/clear, 雨天/rain, 雷雨/thunder");
                        return;
                }
                
                // 广播天气变更消息
                String worldName = getWorldDisplayName(targetWorld);
                server.getPlayerManager().broadcast(
                    Text.of("[AI Misaki] " + result.get() + " (世界: " + worldName + ")"), 
                    false
                );
                
            } catch (Exception e) {
                String errorMsg = "天气变更失败：" + e.getMessage();
                result.set(errorMsg);
                AiMisakiMod.LOGGER.error("变更天气时出错", e);
            }
        });
        
        return result.get();
    }
    
    @Tool(
        name = "set_time",
        description = """
        设置指定世界的时间。支持的时间：
        - day/白天：设置为白天
        - night/夜晚：设置为夜晚
        - noon/正午：设置为正午
        - midnight/午夜：设置为午夜
        - 数字：直接设置游戏时间（0-24000）
        """
    )
    public String setTime(
        @ToolParam(description = "时间类型：day/白天、night/夜晚、noon/正午、midnight/午夜，或具体数字(0-24000)") String timeType,
        @ToolParam(description = "目标世界，可选：overworld(主世界)、nether(下界)、end(末地)，默认主世界") String world
    ) {
        ServerWorld targetWorld = getTargetWorld(world);
        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }
        
        AtomicReference<String> result = new AtomicReference<>("时间设置失败");
        
        runOnMainAndWait(() -> {
            try {
                long gameTime = parseTimeType(timeType);
                if (gameTime == -1) {
                    result.set("❌ 未知的时间类型：" + timeType + "。支持：day/白天, night/夜晚, noon/正午, midnight/午夜，或0-24000的数字");
                    return;
                }
                
                targetWorld.setTimeOfDay(gameTime);
                
                String timeName = getTimeDisplayName(gameTime);
                String worldName = getWorldDisplayName(targetWorld);
                
                result.set("🕐 时间已设置为 " + timeName + " (世界: " + worldName + ")");
                
                // 广播时间变更消息
                server.getPlayerManager().broadcast(
                    Text.of("[AI Misaki] " + result.get()), 
                    false
                );
                
            } catch (Exception e) {
                String errorMsg = "时间设置失败：" + e.getMessage();
                result.set(errorMsg);
                AiMisakiMod.LOGGER.error("设置时间时出错", e);
            }
        });
        
        return result.get();
    }
    
    private String parseWeatherType(String weatherType) {
        String lower = weatherType.toLowerCase().trim();
        switch (lower) {
            case "晴":
            case "晴天":
            case "clear":
            case "sunny":
                return "clear";
            case "雨":
            case "雨天":
            case "下雨":
            case "rain":
            case "rainy":
                return "rain";
            case "雷":
            case "雷雨":
            case "雷暴":
            case "thunder":
            case "thunderstorm":
            case "storm":
                return "thunder";
            default:
                return weatherType;
        }
    }
    
    private long parseTimeType(String timeType) {
        String lower = timeType.toLowerCase().trim();
        
        switch (lower) {
            case "day":
            case "白天":
            case "早上":
            case "morning":
                return 1000L; // 早上
            case "noon":
            case "正午":
            case "中午":
                return 6000L; // 正午
            case "night":
            case "夜晚":
            case "晚上":
            case "evening":
                return 13000L; // 夜晚
            case "midnight":
            case "午夜":
            case "深夜":
                return 18000L; // 午夜
            default:
                try {
                    long time = Long.parseLong(timeType);
                    if (time >= 0 && time <= 24000) {
                        return time;
                    }
                } catch (NumberFormatException ignored) {}
                return -1;
        }
    }
    
    private String getTimeDisplayName(long gameTime) {
        if (gameTime >= 0 && gameTime < 6000) return "白天";
        if (gameTime >= 6000 && gameTime < 12000) return "正午";
        if (gameTime >= 12000 && gameTime < 18000) return "夜晚";
        return "午夜";
    }
    
    private ServerWorld getTargetWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        
        String lower = worldName.toLowerCase().trim();
        switch (lower) {
            case "overworld":
            case "主世界":
            case "地上":
                return server.getWorld(World.OVERWORLD);
            case "nether":
            case "下界":
            case "地狱":
                return server.getWorld(World.NETHER);
            case "end":
            case "末地":
            case "末路之地":
                return server.getWorld(World.END);
            default:
                return null;
        }
    }
    
    private String getWorldDisplayName(ServerWorld world) {
        if (world.getRegistryKey() == World.OVERWORLD) return "主世界";
        if (world.getRegistryKey() == World.NETHER) return "下界";
        if (world.getRegistryKey() == World.END) return "末地";
        return world.getRegistryKey().getValue().toString();
    }
    
    private void runOnMainAndWait(Runnable task) {
        if (server.isOnThread()) {
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        server.execute(() -> {
            try { task.run(); } finally { latch.countDown(); }
        });
        try { latch.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}