package com.hinadt.ai;

import com.hinadt.AusukaAiMod;
import com.hinadt.persistence.MyBatisSupport;
import com.hinadt.persistence.mapper.ConversationMapper;
import com.hinadt.persistence.mapper.LocationMapper;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;

/**
 * 基于H2数据库的对话记忆系统
 * 提供持久化的对话上下文存储和检索功能
 * 数据存储在 ./config/ausuka-ai/ 目录下
 */
public class ConversationMemorySystem {

    // 每个玩家的会话ID
    private final Map<String, String> playerSessions = new ConcurrentHashMap<>();
    // 每个玩家的对话历史缓存
    private final Map<String, List<ConversationRecord>> conversationCache = new ConcurrentHashMap<>();

    public ConversationMemorySystem() {
        // Ensure MyBatis initialized and schema ready
        MyBatisSupport.init();
    }
    
    /**
     * 获取玩家的对话历史上下文
     */
    public String getConversationContext(String playerName) {
        List<ConversationRecord> history = getRecentConversation(playerName, 10);
        if (history.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("## 对话历史上下文 ##\n");
        for (ConversationRecord record : history) {
            context.append(String.format("[%s] %s: %s\n", 
                record.timestamp.toString().substring(11, 19), // 只显示时间部分
                record.messageType.equals("USER") ? "玩家" : "AI",
                record.content));
        }
        context.append("## 当前对话 ##\n");
        
        return context.toString();
    }
    
    /**
     * 保存用户消息
     */
    public void saveUserMessage(String playerName, String message) {
        String sessionId = getCurrentSessionId(playerName);
        saveMessage(playerName, sessionId, "USER", message, "{}");
        
        // 更新缓存
        updateCache(playerName, new ConversationRecord(sessionId, "USER", message, LocalDateTime.now(), "{}"));
    }
    
    /**
     * 保存AI响应
     */
    public void saveAiResponse(String playerName, String response) {
        String sessionId = getCurrentSessionId(playerName);
        saveMessage(playerName, sessionId, "AI", response, "{}");
        
        // 更新缓存
        updateCache(playerName, new ConversationRecord(sessionId, "AI", response, LocalDateTime.now(), "{}"));
    }
    
    /**
     * 开始新的对话会话
     */
    public String startNewConversation(String playerName) {
        String newSessionId = UUID.randomUUID().toString().substring(0, 8);
        playerSessions.put(playerName, newSessionId);
        
        // 清除缓存
        conversationCache.remove(playerName);
        
        // 保存会话开始标记
        saveMessage(playerName, newSessionId, "SYSTEM", "新对话会话开始", "{}");
        
        return newSessionId;
    }
    
    /**
     * 获取当前会话ID
     */
    public String getCurrentSessionId(String playerName) {
        return playerSessions.computeIfAbsent(playerName, k -> {
            // 尝试从数据库获取最近的会话ID
            try (var session = MyBatisSupport.getFactory().openSession()) {
                ConversationMapper mapper = session.getMapper(ConversationMapper.class);
                String latest = mapper.getLatestSessionId(playerName);
                if (latest != null) return latest;
            } catch (Exception e) {
                AusukaAiMod.LOGGER.error("获取会话ID失败", e);
            }
            
            // 创建新会话
            return startNewConversation(playerName);
        });
    }
    
    /**
     * 获取最近的对话记录
     */
    private List<ConversationRecord> getRecentConversation(String playerName, int limit) {
        String sessionId = getCurrentSessionId(playerName);
        List<ConversationRecord> records = new ArrayList<>();
        
        try (var session = MyBatisSupport.getFactory().openSession()) {
            ConversationMapper mapper = session.getMapper(ConversationMapper.class);
            var rows = mapper.getRecent(playerName, sessionId, limit);
            for (var r : rows) {
                records.add(new ConversationRecord(
                    sessionId,
                    (String) r.get("message_type"),
                    (String) r.get("content"),
                    ((java.sql.Timestamp) r.get("timestamp")).toLocalDateTime(),
                    (String) r.get("context_data")
                ));
            }
            java.util.Collections.reverse(records);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("获取对话记录失败", e);
        }
        
        return records;
    }
    
    /**
     * 保存消息到数据库
     */
    private void saveMessage(String playerName, String sessionId, String messageType, String content, String contextData) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            ConversationMapper mapper = session.getMapper(ConversationMapper.class);
            mapper.insertMessage(playerName, sessionId, messageType, content, contextData);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("保存对话记录失败", e);
        }
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(String playerName, ConversationRecord record) {
        conversationCache.computeIfAbsent(playerName, k -> new ArrayList<>()).add(record);
        
        // 保持缓存大小
        List<ConversationRecord> cache = conversationCache.get(playerName);
        if (cache.size() > 50) {
            cache.remove(0);
        }
    }
    
    /**
     * 关闭数据库连接
     */
    public void shutdown() { /* Managed by pool, nothing to do */ }
    
    // 不再暴露底层连接
    
    /**
     * 对话记录数据类
     */
    public static class ConversationRecord {
        public final String sessionId;
        public final String messageType;
        public final String content;
        public final LocalDateTime timestamp;
        public final String contextData;
        
        public ConversationRecord(String sessionId, String messageType, String content, 
                                LocalDateTime timestamp, String contextData) {
            this.sessionId = sessionId;
            this.messageType = messageType;
            this.content = content;
            this.timestamp = timestamp;
            this.contextData = contextData;
        }
    }
    
    /**
     * 位置数据类 - 用于位置记忆功能
     */
    public static class LocationData {
        public final String name;
        public final String world;
        public final double x;
        public final double y; 
        public final double z;
        public final String description;
        
        public LocationData(String name, String world, double x, double y, double z, String description) {
            this.name = name;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.description = description;
        }
        
        public Vec3d toVec3d() {
            return new Vec3d(x, y, z);
        }
    }
    
    // ==================== 位置记忆功能 ====================
    
    /**
     * 保存玩家位置记忆
     */
    public void saveLocation(String playerName, String locationName, String world, double x, double y, double z, String description) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            mapper.upsert(playerName, locationName, world, x, y, z, description);
            AusukaAiMod.LOGGER.info("位置记忆已保存: {} - {} 在 {} ({}, {}, {})", playerName, locationName, world, x, y, z);
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("保存位置记忆失败", e);
        }
    }
    
    /**
     * 获取指定位置记忆 - 支持模糊匹配
     */
    public LocationData getLocationForTeleport(String playerName, String destination) {
        // 首先尝试精确匹配
        LocationData exact = getExactLocation(playerName, destination);
        if (exact != null) {
            return exact;
        }
        
        // 然后尝试模糊匹配
        return getFuzzyLocation(playerName, destination);
    }
    
    /**
     * 精确匹配位置
     */
    private LocationData getExactLocation(String playerName, String locationName) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            var row = mapper.getExact(playerName, locationName);
            if (row != null) {
                return new LocationData(
                        (String) row.get("location_name"),
                        (String) row.get("world"),
                        ((Number) row.get("x")).doubleValue(),
                        ((Number) row.get("y")).doubleValue(),
                        ((Number) row.get("z")).doubleValue(),
                        (String) row.get("description")
                );
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("获取位置记忆失败", e);
        }
        
        return null;
    }
    
    /**
     * 模糊匹配位置
     */
    private LocationData getFuzzyLocation(String playerName, String searchTerm) {
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            var row = mapper.getFuzzy(playerName, "%" + searchTerm + "%");
            if (row != null) {
                return new LocationData(
                        (String) row.get("location_name"),
                        (String) row.get("world"),
                        ((Number) row.get("x")).doubleValue(),
                        ((Number) row.get("y")).doubleValue(),
                        ((Number) row.get("z")).doubleValue(),
                        (String) row.get("description")
                );
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("模糊搜索位置失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取玩家所有位置记忆
     */
    public List<LocationData> getAllLocations(String playerName) {
        List<LocationData> locations = new ArrayList<>();
        
        try (var session = MyBatisSupport.getFactory().openSession()) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            var rows = mapper.getAll(playerName);
            for (var r : rows) {
                locations.add(new LocationData(
                        (String) r.get("location_name"),
                        (String) r.get("world"),
                        ((Number) r.get("x")).doubleValue(),
                        ((Number) r.get("y")).doubleValue(),
                        ((Number) r.get("z")).doubleValue(),
                        (String) r.get("description")
                ));
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("获取所有位置记忆失败", e);
        }
        
        return locations;
    }
    
    /**
     * 删除位置记忆
     */
    public boolean deleteLocation(String playerName, String locationName) {
        try (var session = MyBatisSupport.getFactory().openSession(true)) {
            LocationMapper mapper = session.getMapper(LocationMapper.class);
            int deleted = mapper.delete(playerName, locationName);
            return deleted > 0;
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("删除位置记忆失败", e);
            return false;
        }
    }
}
