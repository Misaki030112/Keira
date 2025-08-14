package com.hinadt.ai;

import com.hinadt.AiMisakiMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.math.Vec3d;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Memory System - 提供AI记忆功能
 * 支持保存和回忆玩家定义的位置、偏好设置等信息
 */
public class MemorySystem {
    
    private static final String MEMORY_FILE = "ai_memory.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // 内存中的数据缓存
    private static final Map<String, Map<String, Object>> playerMemories = new ConcurrentHashMap<>();
    private static final Map<String, LocationData> savedLocations = new ConcurrentHashMap<>();
    private static final Map<String, String> globalMemory = new ConcurrentHashMap<>();
    
    static {
        loadMemoryFromFile();
    }
    
    @Tool(
        name = "save_location",
        description = """
        保存一个位置到AI记忆中。当玩家说"这里是我的家"、"记住这个地方叫XX"等时使用此工具。
        这让AI能够记住玩家定义的重要位置，以后可以传送到这些位置。
        """
    )
    public String saveLocation(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "位置名称，如'家'、'农场'、'矿洞'等") String locationName,
        @ToolParam(description = "X坐标") double x,
        @ToolParam(description = "Y坐标") double y,
        @ToolParam(description = "Z坐标") double z,
        @ToolParam(description = "世界名称，如'overworld'、'nether'、'end'") String world,
        @ToolParam(description = "位置描述，可选") String description
    ) {
        String key = playerName + ":" + locationName.toLowerCase();
        LocationData location = new LocationData(x, y, z, world, locationName, description, playerName);
        savedLocations.put(key, location);
        
        saveMemoryToFile();
        
        return String.format("✅ 已保存位置：%s 的 '%s' (%.1f, %.1f, %.1f) 在 %s", 
            playerName, locationName, x, y, z, world);
    }
    
    @Tool(
        name = "get_saved_location",
        description = """
        获取保存的位置信息。当玩家想要传送到之前保存的位置时使用。
        可以获取特定玩家的位置或搜索所有相关位置。
        """
    )
    public String getSavedLocation(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "位置名称") String locationName
    ) {
        String key = playerName + ":" + locationName.toLowerCase();
        LocationData location = savedLocations.get(key);
        
        if (location == null) {
            // 尝试模糊匹配
            for (Map.Entry<String, LocationData> entry : savedLocations.entrySet()) {
                if (entry.getKey().startsWith(playerName + ":") && 
                    entry.getValue().name.toLowerCase().contains(locationName.toLowerCase())) {
                    location = entry.getValue();
                    break;
                }
            }
        }
        
        if (location == null) {
            return "❌ 找不到 " + playerName + " 保存的位置：" + locationName;
        }
        
        return String.format("📍 找到位置：%s (%.1f, %.1f, %.1f) 在 %s%s", 
            location.name, location.x, location.y, location.z, location.world,
            location.description != null ? " - " + location.description : "");
    }
    
    @Tool(
        name = "list_saved_locations",
        description = """
        列出玩家保存的所有位置。帮助玩家查看他们之前记录的所有地点。
        """
    )
    public String listSavedLocations(
        @ToolParam(description = "玩家名称") String playerName
    ) {
        StringBuilder result = new StringBuilder();
        result.append("📋 ").append(playerName).append(" 保存的位置：\n");
        
        boolean found = false;
        for (Map.Entry<String, LocationData> entry : savedLocations.entrySet()) {
            if (entry.getKey().startsWith(playerName + ":")) {
                LocationData loc = entry.getValue();
                result.append("• ").append(loc.name)
                      .append(" (").append((int)loc.x).append(", ").append((int)loc.y).append(", ").append((int)loc.z).append(")")
                      .append(" 在 ").append(loc.world);
                if (loc.description != null) {
                    result.append(" - ").append(loc.description);
                }
                result.append("\n");
                found = true;
            }
        }
        
        if (!found) {
            result.append("暂无保存的位置");
        }
        
        return result.toString();
    }
    
    @Tool(
        name = "save_player_preference",
        description = """
        保存玩家的偏好设置或个人信息。如玩家喜欢的建筑风格、常用材料、游戏习惯等。
        这让AI能够更好地理解玩家需求并提供个性化建议。
        """
    )
    public String savePlayerPreference(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "偏好类别，如'建筑风格'、'材料偏好'、'游戏目标'等") String category,
        @ToolParam(description = "偏好内容") String preference
    ) {
        playerMemories.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>())
                     .put(category, preference);
        
        saveMemoryToFile();
        
        return String.format("✅ 已保存 %s 的偏好：%s = %s", playerName, category, preference);
    }
    
    @Tool(
        name = "get_player_preference",
        description = """
        获取玩家的偏好设置。用于提供个性化的建议和服务。
        """
    )
    public String getPlayerPreference(
        @ToolParam(description = "玩家名称") String playerName,
        @ToolParam(description = "偏好类别") String category
    ) {
        Map<String, Object> preferences = playerMemories.get(playerName);
        if (preferences == null || !preferences.containsKey(category)) {
            return "❌ 未找到 " + playerName + " 的 " + category + " 偏好设置";
        }
        
        return String.format("📝 %s 的 %s：%s", playerName, category, preferences.get(category));
    }
    
    @Tool(
        name = "save_global_memory",
        description = """
        保存全局记忆信息，如服务器规则、重要事件、公共建筑等。
        所有玩家共享的信息存储在这里。
        """
    )
    public String saveGlobalMemory(
        @ToolParam(description = "记忆键名") String key,
        @ToolParam(description = "记忆内容") String value
    ) {
        globalMemory.put(key, value);
        saveMemoryToFile();
        
        return String.format("✅ 已保存全局记忆：%s = %s", key, value);
    }
    
    @Tool(
        name = "get_global_memory",
        description = """
        获取全局记忆信息。用于回忆服务器的重要信息。
        """
    )
    public String getGlobalMemory(
        @ToolParam(description = "记忆键名") String key
    ) {
        String value = globalMemory.get(key);
        if (value == null) {
            return "❌ 未找到全局记忆：" + key;
        }
        
        return String.format("🧠 全局记忆 %s：%s", key, value);
    }
    
    /**
     * 获取位置数据用于传送工具
     */
    public static LocationData getLocationForTeleport(String playerName, String locationName) {
        String key = playerName + ":" + locationName.toLowerCase();
        LocationData location = savedLocations.get(key);
        
        if (location == null) {
            // 尝试模糊匹配
            for (Map.Entry<String, LocationData> entry : savedLocations.entrySet()) {
                if (entry.getKey().startsWith(playerName + ":") && 
                    entry.getValue().name.toLowerCase().contains(locationName.toLowerCase())) {
                    return entry.getValue();
                }
            }
        }
        
        return location;
    }
    
    private static void loadMemoryFromFile() {
        try {
            Path memoryPath = Paths.get(MEMORY_FILE);
            if (!Files.exists(memoryPath)) {
                return;
            }
            
            String json = Files.readString(memoryPath);
            Type type = new TypeToken<MemoryData>(){}.getType();
            MemoryData data = gson.fromJson(json, type);
            
            if (data != null) {
                if (data.playerMemories != null) {
                    playerMemories.putAll(data.playerMemories);
                }
                if (data.savedLocations != null) {
                    savedLocations.putAll(data.savedLocations);
                }
                if (data.globalMemory != null) {
                    globalMemory.putAll(data.globalMemory);
                }
            }
            
            AiMisakiMod.LOGGER.info("已加载AI记忆数据：{} 个玩家记忆，{} 个位置，{} 个全局记忆", 
                playerMemories.size(), savedLocations.size(), globalMemory.size());
                
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("加载AI记忆数据失败", e);
        }
    }
    
    private static void saveMemoryToFile() {
        try {
            MemoryData data = new MemoryData();
            data.playerMemories = new HashMap<>(playerMemories);
            data.savedLocations = new HashMap<>(savedLocations);
            data.globalMemory = new HashMap<>(globalMemory);
            
            String json = gson.toJson(data);
            Files.writeString(Paths.get(MEMORY_FILE), json);
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("保存AI记忆数据失败", e);
        }
    }
    
    // 数据类
    public static class LocationData {
        public double x, y, z;
        public String world;
        public String name;
        public String description;
        public String owner;
        
        public LocationData(double x, double y, double z, String world, String name, String description, String owner) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
            this.name = name;
            this.description = description;
            this.owner = owner;
        }
        
        public Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }
    }
    
    private static class MemoryData {
        public Map<String, Map<String, Object>> playerMemories;
        public Map<String, LocationData> savedLocations;
        public Map<String, String> globalMemory;
    }
}