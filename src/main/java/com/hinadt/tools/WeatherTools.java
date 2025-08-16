package com.hinadt.tools;

import com.hinadt.AusukaAiMod;
import com.hinadt.observability.RequestContext;
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
        高级天气控制工具：精确控制游戏世界的天气状况，创造理想的游戏环境。
        
        支持的天气类型：
        - clear/晴天：清除所有降水和雷暴，创造明亮清爽的环境
        - rain/雨天：启动降雨天气，适合农业活动和水资源收集
        - thunder/雷雨：激活雷暴天气，产生闪电和雷声效果
        
        智能特性：
        - 精确时长控制：可自定义天气持续时间
        - 多世界支持：可指定不同维度的天气
        - 即时生效：天气变化立即在所有玩家客户端同步
        - 自然过渡：天气变化具有自然的视觉过渡效果
        
        使用场景：
        - 建筑摄影：创造完美的拍摄环境
        - 农业活动：雨天促进作物生长
        - 冒险氛围：雷雨天增加探险刺激感
        - 活动组织：为特殊活动设置合适天气
        
        技术优势：
        - 服务器级别控制，影响所有玩家
        - 兼容原版天气循环系统
        - 支持持续时间精确控制
        """
    )
    public String changeWeather(
        @ToolParam(description = "天气类型：clear/晴天、rain/雨天、thunder/雷雨") String weatherType,
        @ToolParam(description = "持续时间（秒），可选，默认600秒（10分钟）") Integer duration,
        @ToolParam(description = "目标世界，可选：overworld(主世界)、nether(下界)、end(末地)，默认主世界") String world
    ) {
        AusukaAiMod.LOGGER.debug("{} [tool:change_weather] params type='{}' duration={} world='{}'",
                RequestContext.midTag(), weatherType, duration, world);
        ServerWorld targetWorld = getTargetWorld(world);
        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }
        
        final ServerWorld finalTargetWorld = targetWorld;
        final int weatherDuration = (duration != null && duration > 0) ? duration * 20 : 12000; // 转换为游戏tick
        
        AtomicReference<String> result = new AtomicReference<>("Weather change failed");
        
        runOnMainAndWait(() -> {
            try {
                String weatherName = parseWeatherType(weatherType);
                
                switch (weatherName.toLowerCase()) {
                    case "clear":
                    case "晴天":
                        finalTargetWorld.setWeather(weatherDuration, 0, false, false);
                        result.set("Clear weather set for " + (weatherDuration/20) + " seconds");
                        break;
                        
                    case "rain":
                    case "雨天":
                        finalTargetWorld.setWeather(0, weatherDuration, true, false);
                        result.set("Rainy weather set for " + (weatherDuration/20) + " seconds");
                        break;
                        
                    case "thunder":
                    case "雷雨":
                        finalTargetWorld.setWeather(0, weatherDuration, true, true);
                        result.set("Thunderstorm set for " + (weatherDuration/20) + " seconds");
                        break;
                        
                    default:
                        result.set("Unknown weather type: " + weatherType + ". Supported: clear, rain, thunder");
                        return;
                }
                
                String worldName = getWorldDisplayName(finalTargetWorld);
                AusukaAiMod.LOGGER.debug("{} [tool:change_weather] result='{}' world='{}'",
                        RequestContext.midTag(), result.get(), worldName);
                
            } catch (Exception e) {
                String errorMsg = "Weather change failed: " + e.getMessage();
                result.set(errorMsg);
                AusukaAiMod.LOGGER.error("变更天气时出错", e);
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
        AusukaAiMod.LOGGER.debug("{} [tool:set_time] params type='{}' world='{}'",
                RequestContext.midTag(), timeType, world);
        ServerWorld targetWorld = getTargetWorld(world);
        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }
        
        final ServerWorld finalTargetWorld = targetWorld;
        AtomicReference<String> result = new AtomicReference<>("Time set failed");
        
        runOnMainAndWait(() -> {
            try {
                long gameTime = parseTimeType(timeType);
                if (gameTime == -1) {
                    result.set("Unknown time type: " + timeType + ". Supported: day, night, noon, midnight or 0-24000");
                    return;
                }
                
                finalTargetWorld.setTimeOfDay(gameTime);
                
                String timeKey = resolveTimeKey(gameTime);
                String worldName = getWorldDisplayName(finalTargetWorld);
                
                result.set("Time set to " + timeKey + " (world: " + worldName + ")");
                AusukaAiMod.LOGGER.debug("{} [tool:set_time] result='{}'",
                        RequestContext.midTag(), result.get());
                
            } catch (Exception e) {
                String errorMsg = "Time set failed: " + e.getMessage();
                result.set(errorMsg);
                AusukaAiMod.LOGGER.error("设置时间时出错", e);
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
    
    private String resolveTimeKey(long gameTime) {
        if (gameTime >= 0 && gameTime < 6000) return "day";
        if (gameTime >= 6000 && gameTime < 12000) return "noon";
        if (gameTime >= 12000 && gameTime < 18000) return "night";
        return "midnight";
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
