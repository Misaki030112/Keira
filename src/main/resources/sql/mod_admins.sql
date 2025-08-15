-- MOD管理员权限相关查询

-- 添加MOD管理员
INSERT INTO mod_admins (player_name, permission_level, granted_by, notes) 
VALUES (?, ?, ?, ?);

-- 检查玩家是否为MOD管理员
SELECT permission_level FROM mod_admins 
WHERE player_name = ? AND is_active = TRUE;

-- 移除MOD管理员权限
UPDATE mod_admins 
SET is_active = FALSE 
WHERE player_name = ?;

-- 更新管理员权限级别
UPDATE mod_admins 
SET permission_level = ?, notes = ? 
WHERE player_name = ?;

-- 获取所有活跃的MOD管理员
SELECT player_name, permission_level, granted_by, granted_at, notes 
FROM mod_admins 
WHERE is_active = TRUE 
ORDER BY permission_level DESC, granted_at ASC;

-- 获取管理员操作历史
SELECT player_name, permission_level, granted_by, granted_at, is_active, notes 
FROM mod_admins 
WHERE player_name = ? 
ORDER BY granted_at DESC;