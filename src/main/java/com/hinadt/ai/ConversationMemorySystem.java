package com.hinadt.ai;

import com.hinadt.AiMisakiMod;

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
    
    // 数据库配置 - 存储在MOD配置目录下
    private static final String DB_DIR = "./config/ausuka-ai/";
    private static final String DB_URL = "jdbc:h2:" + DB_DIR + "conversations;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "ausuka";
    private static final String DB_PASSWORD = "";
    
    // 每个玩家的会话ID
    private final Map<String, String> playerSessions = new ConcurrentHashMap<>();
    // 每个玩家的对话历史缓存
    private final Map<String, List<ConversationRecord>> conversationCache = new ConcurrentHashMap<>();
    
    private Connection connection;
    
    public ConversationMemorySystem() {
        initializeDatabase();
    }
    
    /**
     * 初始化H2数据库
     */
    private void initializeDatabase() {
        try {
            // 确保数据库目录存在
            java.io.File dbDir = new java.io.File(DB_DIR);
            if (!dbDir.exists()) {
                boolean created = dbDir.mkdirs();
                if (created) {
                    AiMisakiMod.LOGGER.info("创建数据库目录: {}", DB_DIR);
                }
            }
            
            // 加载H2驱动
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // 使用SQL文件创建表结构
            String initSql = SqlQueryLoader.loadSqlFile("init.sql");
            String[] statements = initSql.split(";");
            
            try (Statement stmt = connection.createStatement()) {
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
                AiMisakiMod.LOGGER.info("对话记忆数据库初始化完成，数据存储位置: {}", new java.io.File(DB_DIR).getAbsolutePath());
            }
            
        } catch (Exception e) {
            AiMisakiMod.LOGGER.error("初始化对话记忆数据库失败", e);
        }
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
            try (PreparedStatement stmt = connection.prepareStatement(
                SqlQueryLoader.getQuery("conversations.sql", "获取玩家当前会话ID"))) {
                
                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    return rs.getString("session_id");
                }
            } catch (SQLException e) {
                AiMisakiMod.LOGGER.error("获取会话ID失败", e);
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
        
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("conversations.sql", "获取玩家最近的对话记录"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, sessionId);
            stmt.setInt(3, limit);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                records.add(new ConversationRecord(
                    rs.getString("session_id"),
                    rs.getString("message_type"),
                    rs.getString("message_content"),
                    rs.getTimestamp("timestamp").toLocalDateTime(),
                    rs.getString("context_data")
                ));
            }
            
            // 反转顺序，最新的在后面
            java.util.Collections.reverse(records);
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取对话记录失败", e);
        }
        
        return records;
    }
    
    /**
     * 保存消息到数据库
     */
    private void saveMessage(String playerName, String sessionId, String messageType, String content, String contextData) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("conversations.sql", "保存对话记录"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, sessionId);
            stmt.setString(3, messageType);
            stmt.setString(4, content);
            stmt.setString(5, contextData);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("保存对话记录失败", e);
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
     * 清理过期的对话记录
     */
    public void cleanupOldConversations(int daysToKeep) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("conversations.sql", "删除过期的对话记录"))) {
            
            stmt.setInt(1, daysToKeep);
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                AiMisakiMod.LOGGER.info("清理了 {} 条过期对话记录", deleted);
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("清理过期对话记录失败", e);
        }
    }
    
    /**
     * 获取对话统计信息
     */
    public String getConversationStats() {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("conversations.sql", "获取对话统计信息"))) {
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return String.format("总消息数: %d, 活跃玩家: %d, 对话会话: %d",
                    rs.getInt("total_messages"),
                    rs.getInt("unique_players"), 
                    rs.getInt("total_sessions"));
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取对话统计失败", e);
        }
        
        return "统计信息获取失败";
    }
    
    /**
     * 关闭数据库连接
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                AiMisakiMod.LOGGER.info("对话记忆数据库连接已关闭");
            }
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("关闭数据库连接失败", e);
        }
    }
    
    /**
     * 获取数据库连接（供其他系统使用）
     */
    public Connection getConnection() {
        return connection;
    }
    
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
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("locations.sql", "保存位置记忆"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, locationName);
            stmt.setString(3, world);
            stmt.setDouble(4, x);
            stmt.setDouble(5, y);
            stmt.setDouble(6, z);
            stmt.setString(7, description);
            
            stmt.executeUpdate();
            AiMisakiMod.LOGGER.info("位置记忆已保存: {} - {} 在 {} ({}, {}, {})", 
                playerName, locationName, world, x, y, z);
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("保存位置记忆失败", e);
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
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("locations.sql", "获取指定位置"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, locationName);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new LocationData(
                    rs.getString("location_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("description")
                );
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取位置记忆失败", e);
        }
        
        return null;
    }
    
    /**
     * 模糊匹配位置
     */
    private LocationData getFuzzyLocation(String playerName, String searchTerm) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("locations.sql", "模糊搜索位置"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, "%" + searchTerm + "%");
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new LocationData(
                    rs.getString("location_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("description")
                );
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("模糊搜索位置失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取玩家所有位置记忆
     */
    public List<LocationData> getAllLocations(String playerName) {
        List<LocationData> locations = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("locations.sql", "获取玩家所有位置"))) {
            
            stmt.setString(1, playerName);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                locations.add(new LocationData(
                    rs.getString("location_name"),
                    rs.getString("world"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("description")
                ));
            }
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取所有位置记忆失败", e);
        }
        
        return locations;
    }
    
    /**
     * 删除位置记忆
     */
    public boolean deleteLocation(String playerName, String locationName) {
        try (PreparedStatement stmt = connection.prepareStatement(
            SqlQueryLoader.getQuery("locations.sql", "删除指定位置"))) {
            
            stmt.setString(1, playerName);
            stmt.setString(2, locationName);
            
            int deleted = stmt.executeUpdate();
            return deleted > 0;
            
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("删除位置记忆失败", e);
            return false;
        }
    }
}