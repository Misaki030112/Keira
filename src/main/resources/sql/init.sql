-- AI MOD 数据库初始化脚本
-- 用于创建所有必要的表结构

-- 对话记忆表
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
);

-- MOD管理员表
CREATE TABLE IF NOT EXISTS mod_admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_name VARCHAR(255) NOT NULL UNIQUE,
    permission_level INT NOT NULL DEFAULT 2,
    granted_by VARCHAR(255),
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    INDEX idx_player_name (player_name),
    INDEX idx_permission_level (permission_level)
);

-- 玩家设置表
CREATE TABLE IF NOT EXISTS player_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_name VARCHAR(255) NOT NULL,
    setting_key VARCHAR(255) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_player_setting (player_name, setting_key),
    INDEX idx_player_name (player_name)
);

-- 自动消息系统配置表
CREATE TABLE IF NOT EXISTS auto_message_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL UNIQUE,
    config_value TEXT,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入默认的自动消息配置
INSERT IGNORE INTO auto_message_config (config_key, config_value, description) VALUES
('system_enabled', 'true', '自动消息系统总开关'),
('broadcast_enabled', 'true', '广播消息开关'),
('personal_enabled', 'true', '个人消息开关'),
('broadcast_interval', '900', '广播消息间隔(秒)'),
('personal_interval', '600', '个人消息间隔(秒)');