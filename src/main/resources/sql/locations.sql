-- 玩家位置记忆系统SQL查询集合

-- 保存位置记忆
-- 保存位置记忆
INSERT INTO player_locations (player_name, location_name, world, x, y, z, description) 
VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE 
    world = VALUES(world),
    x = VALUES(x),
    y = VALUES(y), 
    z = VALUES(z),
    description = VALUES(description),
    saved_at = CURRENT_TIMESTAMP;

-- 获取指定位置
-- 获取指定位置
SELECT location_name, world, x, y, z, description 
FROM player_locations 
WHERE player_name = ? AND location_name = ?;

-- 模糊搜索位置
-- 模糊搜索位置
SELECT location_name, world, x, y, z, description 
FROM player_locations 
WHERE player_name = ? AND location_name LIKE ?
ORDER BY saved_at DESC LIMIT 1;

-- 获取玩家所有位置
-- 获取玩家所有位置
SELECT location_name, world, x, y, z, description, saved_at 
FROM player_locations 
WHERE player_name = ? 
ORDER BY saved_at DESC;

-- 删除指定位置
-- 删除指定位置
DELETE FROM player_locations 
WHERE player_name = ? AND location_name = ?;

-- 获取位置数量
-- 获取位置数量
SELECT COUNT(*) as count 
FROM player_locations 
WHERE player_name = ?;