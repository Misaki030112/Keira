package com.hinadt.ai;

import com.hinadt.AiMisakiMod;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

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
}