package com.hinadt.persistence;

import com.hinadt.AusukaAiMod;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import javax.sql.DataSource;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MyBatis bootstrap and shared SqlSessionFactory holder.
 */
public final class MyBatisSupport {
    private static SqlSessionFactory factory;
    private static DataSource dataSource;

    private MyBatisSupport() {}

    public static synchronized void init() {
        if (factory != null) return;
        try {
            // H2 with MySQL compatibility for legacy SQL if needed, but we will use H2-friendly SQL.
            String url = "jdbc:h2:./config/ausuka-ai/conversations;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
            dataSource = new PooledDataSource("org.h2.Driver", url, "ausuka", "");

            // Ensure schema present (idempotent)
            ensureSchema();

            try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
                factory = new SqlSessionFactoryBuilder().build(reader);
            }
        } catch (Exception e) {
            AusukaAiMod.LOGGER.error("初始化 MyBatis 失败", e);
            throw new RuntimeException(e);
        }
    }

    public static SqlSessionFactory getFactory() {
        if (factory == null) init();
        return factory;
    }

    public static DataSource getDataSource() {
        if (dataSource == null) init();
        return dataSource;
    }

    private static void ensureSchema() throws SQLException {
        try (Connection c = getDataSource().getConnection(); Statement s = c.createStatement()) {
            // Conversations
            s.executeUpdate("CREATE TABLE IF NOT EXISTS conversations (\n" +
                    "id IDENTITY PRIMARY KEY,\n" +
                    "player_name VARCHAR(255) NOT NULL,\n" +
                    "session_id VARCHAR(255) NOT NULL,\n" +
                    "message_type VARCHAR(50) NOT NULL,\n" +
                    "message_content CLOB NOT NULL,\n" +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "context_data CLOB\n" +
                    ")");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conv_player_session ON conversations(player_name, session_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conv_timestamp ON conversations(timestamp)");

            // Mod admins
            s.executeUpdate("CREATE TABLE IF NOT EXISTS mod_admins (\n" +
                    "id IDENTITY PRIMARY KEY,\n" +
                    "player_name VARCHAR(255) NOT NULL UNIQUE,\n" +
                    "permission_level INT NOT NULL DEFAULT 2,\n" +
                    "granted_by VARCHAR(255),\n" +
                    "granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "is_active BOOLEAN DEFAULT TRUE,\n" +
                    "notes CLOB\n" +
                    ")");

            // Player locations
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_locations (\n" +
                    "id IDENTITY PRIMARY KEY,\n" +
                    "player_name VARCHAR(255) NOT NULL,\n" +
                    "location_name VARCHAR(255) NOT NULL,\n" +
                    "world VARCHAR(255) NOT NULL,\n" +
                    "x DOUBLE NOT NULL,\n" +
                    "y DOUBLE NOT NULL,\n" +
                    "z DOUBLE NOT NULL,\n" +
                    "description CLOB,\n" +
                    "saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                    "UNIQUE(player_name, location_name)\n" +
                    ")");

            // AI chat sessions
            s.executeUpdate("CREATE TABLE IF NOT EXISTS ai_chat_sessions (\n" +
                    "player_name VARCHAR(255) PRIMARY KEY,\n" +
                    "in_chat BOOLEAN NOT NULL,\n" +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n" +
                    ")");
        }
    }
}
