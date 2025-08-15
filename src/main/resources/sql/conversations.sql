-- 对话记忆相关查询

-- 保存对话记录
INSERT INTO conversations (player_name, session_id, message_type, message_content, context_data) 
VALUES (?, ?, ?, ?, ?);

-- 获取玩家最近的对话记录
SELECT * FROM conversations 
WHERE player_name = ? AND session_id = ? 
ORDER BY timestamp DESC 
LIMIT ?;

-- 获取玩家当前会话ID
SELECT DISTINCT session_id FROM conversations 
WHERE player_name = ? 
ORDER BY timestamp DESC 
LIMIT 1;

-- 删除过期的对话记录
DELETE FROM conversations 
WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY);

-- 获取对话统计信息
SELECT 
    COUNT(*) as total_messages,
    COUNT(DISTINCT player_name) as unique_players,
    COUNT(DISTINCT session_id) as total_sessions
FROM conversations;