package com.hinadt.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * SQL查询加载器
 * 从资源文件加载SQL查询，实现代码与SQL分离
 */
public class SqlQueryLoader {
    
    private static final Map<String, String> queryCache = new HashMap<>();
    
    /**
     * 加载SQL文件内容
     */
    public static String loadSqlFile(String fileName) {
        if (queryCache.containsKey(fileName)) {
            return queryCache.get(fileName);
        }
        
        try (InputStream is = SqlQueryLoader.class.getResourceAsStream("/sql/" + fileName)) {
            if (is == null) {
                throw new RuntimeException("SQL文件不存在: " + fileName);
            }
            
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            queryCache.put(fileName, content);
            return content;
            
        } catch (IOException e) {
            throw new RuntimeException("加载SQL文件失败: " + fileName, e);
        }
    }
    
    /**
     * 从SQL文件中提取特定查询
     * 使用注释标记分隔不同的查询
     */
    public static String getQuery(String fileName, String queryName) {
        String content = loadSqlFile(fileName);
        String cacheKey = fileName + ":" + queryName;
        
        if (queryCache.containsKey(cacheKey)) {
            return queryCache.get(cacheKey);
        }
        
        // 简单实现：查找以 "-- queryName" 开头的注释后的SQL
        String marker = "-- " + queryName;
        String[] lines = content.split("\n");
        StringBuilder query = new StringBuilder();
        boolean collecting = false;
        
        for (String line : lines) {
            if (line.trim().startsWith(marker)) {
                collecting = true;
                continue;
            }
            
            if (collecting) {
                if (line.trim().startsWith("-- ") && !line.trim().equals(marker)) {
                    // 遇到新的注释标记，停止收集
                    break;
                }
                
                if (!line.trim().isEmpty() && !line.trim().startsWith("--")) {
                    query.append(line).append("\n");
                }
            }
        }
        
        String result = query.toString().trim();
        if (result.isEmpty()) {
            throw new RuntimeException("未找到查询: " + queryName + " 在文件: " + fileName);
        }
        
        queryCache.put(cacheKey, result);
        return result;
    }
    
    // 预定义的查询名称常量
    public static class Queries {
        
        public static class Conversations {
            public static final String SAVE_MESSAGE = getQuery("conversations.sql", "保存对话记录");
            public static final String GET_RECENT = getQuery("conversations.sql", "获取玩家最近的对话记录");
            public static final String GET_CURRENT_SESSION = getQuery("conversations.sql", "获取玩家当前会话ID");
            public static final String DELETE_OLD = getQuery("conversations.sql", "删除过期的对话记录");
            public static final String GET_STATS = getQuery("conversations.sql", "获取对话统计信息");
        }
        
        public static class ModAdmins {
            public static final String ADD_ADMIN = getQuery("mod_admins.sql", "添加MOD管理员");
            public static final String CHECK_ADMIN = getQuery("mod_admins.sql", "检查玩家是否为MOD管理员");
            public static final String REMOVE_ADMIN = getQuery("mod_admins.sql", "移除MOD管理员权限");
            public static final String UPDATE_PERMISSION = getQuery("mod_admins.sql", "更新管理员权限级别");
            public static final String LIST_ADMINS = getQuery("mod_admins.sql", "获取所有活跃的MOD管理员");
            public static final String GET_HISTORY = getQuery("mod_admins.sql", "获取管理员操作历史");
        }
    }
    
    /**
     * 清空查询缓存（用于开发时重新加载）
     */
    public static void clearCache() {
        queryCache.clear();
    }
}