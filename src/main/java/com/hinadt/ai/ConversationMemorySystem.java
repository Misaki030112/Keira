package com.hinadt.ai;

import com.hinadt.AiMisakiMod;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简化的会话记忆管理系统
 * 使用嵌入式H2数据库存储对话历史
 * 提供对话上下文管理和搜索功能
 */
public class ConversationMemorySystem {
    
    private static final String DB_URL = "jdbc:h2:./ai_conversations;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
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
            // 加载H2驱动
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // 创建表结构
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS conversations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_name VARCHAR(255) NOT NULL,
                    session_id VARCHAR(255) NOT NULL,
                    message_type VARCHAR(50) NOT NULL,
                    message_content TEXT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    context_data TEXT,
                    INDEX idx_player_session (player_name, session_id),
                    INDEX idx_timestamp (timestamp)
                )
                """;
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
                AiMisakiMod.LOGGER.info("对话记忆数据库初始化完成");
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
        String newSessionId = playerName + "_" + System.currentTimeMillis();
        playerSessions.put(playerName, newSessionId);
        
        // 清空缓存
        conversationCache.remove(playerName);
        
        // 记录新会话开始
        saveMessage(playerName, newSessionId, "SYSTEM", "开始新对话会话", "{}");
        
        AiMisakiMod.LOGGER.info("玩家 {} 开始新对话会话: {}", playerName, newSessionId);
        return newSessionId;
    }
    
    /**
     * 获取当前会话ID
     */
    public String getCurrentSessionId(String playerName) {
        return playerSessions.computeIfAbsent(playerName, k -> 
            playerName + "_" + System.currentTimeMillis());
    }
    
    /**
     * 获取最近的对话记录
     */
    private List<ConversationRecord> getRecentConversation(String playerName, int limit) {
        // 先从缓存获取
        List<ConversationRecord> cached = conversationCache.get(playerName);
        if (cached != null && cached.size() >= limit) {
            int startIndex = Math.max(0, cached.size() - limit);
            return cached.subList(startIndex, cached.size());
        }
        
        // 从数据库获取
        String sessionId = getCurrentSessionId(playerName);
        String sql = """
            SELECT message_type, message_content, timestamp, context_data
            FROM conversations 
            WHERE player_name = ? AND session_id = ? AND message_type IN ('USER', 'AI')
            ORDER BY timestamp ASC 
            LIMIT ?
            """;
        
        List<ConversationRecord> results = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setString(2, sessionId);
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ConversationRecord record = new ConversationRecord(
                        sessionId,
                        rs.getString("message_type"),
                        rs.getString("message_content"),
                        rs.getTimestamp("timestamp").toLocalDateTime(),
                        rs.getString("context_data")
                    );
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取对话历史失败: " + playerName, e);
        }
        
        // 更新缓存
        conversationCache.put(playerName, new ArrayList<>(results));
        
        return results;
    }
    
    /**
     * 保存消息到数据库
     */
    private void saveMessage(String playerName, String sessionId, String messageType, String content, String contextData) {
        String sql = """
            INSERT INTO conversations (player_name, session_id, message_type, message_content, context_data) 
            VALUES (?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setString(2, sessionId);
            stmt.setString(3, messageType);
            stmt.setString(4, content);
            stmt.setString(5, contextData != null ? contextData : "{}");
            stmt.executeUpdate();
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("保存对话消息失败", e);
        }
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(String playerName, ConversationRecord record) {
        conversationCache.computeIfAbsent(playerName, k -> new ArrayList<>()).add(record);
        
        // 限制缓存大小
        List<ConversationRecord> cache = conversationCache.get(playerName);
        if (cache.size() > 20) {
            cache.remove(0); // 移除最旧的记录
        }
    }
    
    /**
     * 搜索相关对话历史
     */
    public List<ConversationRecord> searchConversations(String playerName, String keyword, int limit) {
        String sql = """
            SELECT session_id, message_type, message_content, timestamp, context_data
            FROM conversations 
            WHERE player_name = ? AND message_content LIKE ? 
            ORDER BY timestamp DESC 
            LIMIT ?
            """;
        
        List<ConversationRecord> results = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            stmt.setString(2, "%" + keyword + "%");
            stmt.setInt(3, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ConversationRecord record = new ConversationRecord(
                        rs.getString("session_id"),
                        rs.getString("message_type"),
                        rs.getString("message_content"),
                        rs.getTimestamp("timestamp").toLocalDateTime(),
                        rs.getString("context_data")
                    );
                    results.add(record);
                }
            }
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("搜索对话历史失败", e);
        }
        
        return results;
    }
    
    /**
     * 获取玩家的会话统计
     */
    public ConversationStats getPlayerStats(String playerName) {
        String sql = """
            SELECT 
                COUNT(*) as total_messages,
                COUNT(DISTINCT session_id) as total_sessions,
                MAX(timestamp) as last_activity
            FROM conversations 
            WHERE player_name = ?
            """;
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new ConversationStats(
                        rs.getInt("total_messages"),
                        rs.getInt("total_sessions"),
                        rs.getTimestamp("last_activity") != null ? 
                            rs.getTimestamp("last_activity").toLocalDateTime() : null
                    );
                }
            }
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("获取对话统计失败", e);
        }
        
        return new ConversationStats(0, 0, null);
    }
    
    /**
     * 清理旧的对话记录
     */
    public void cleanupOldConversations(int daysToKeep) {
        String sql = "DELETE FROM conversations WHERE timestamp < DATEADD('DAY', ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, -daysToKeep);
            int deleted = stmt.executeUpdate();
            AiMisakiMod.LOGGER.info("清理了 {} 条旧对话记录", deleted);
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("清理旧对话记录失败", e);
        }
    }
    
    /**
     * 关闭数据库连接
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            AiMisakiMod.LOGGER.error("关闭对话记忆数据库失败", e);
        }
    }
    
    // 数据类
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
    
    public static class ConversationStats {
        public final int totalMessages;
        public final int totalSessions;
        public final LocalDateTime lastActivity;
        
        public ConversationStats(int totalMessages, int totalSessions, LocalDateTime lastActivity) {
            this.totalMessages = totalMessages;
            this.totalSessions = totalSessions;
            this.lastActivity = lastActivity;
        }
    }
}