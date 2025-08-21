package com.keira.persistence;

import com.keira.KeiraAiMod;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import javax.sql.DataSource;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            String url = "jdbc:h2:./config/keira-ai/conversations;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
            dataSource = new PooledDataSource("org.h2.Driver", url, "keira", "");

            // Ensure schema present (idempotent)
            ensureSchema();

            try (Reader reader = Resources.getResourceAsReader("mybatis-config.xml")) {
                factory = new SqlSessionFactoryBuilder().build(reader);
            }
            // Log DB absolute path for troubleshooting
            try {
                Path dbFile = Paths.get("./config/keira-ai/conversations.mv.db").toAbsolutePath();
                KeiraAiMod.LOGGER.info("H2 database initialized: {}", dbFile);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            KeiraAiMod.LOGGER.error("Failed to initialize MyBatis", e);
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
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id IDENTITY PRIMARY KEY,
                        player_name VARCHAR(255) NOT NULL,
                        session_id VARCHAR(255) NOT NULL,
                        message_type VARCHAR(50) NOT NULL,
                        message_content CLOB NOT NULL,
                        `timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        context_data CLOB
                    )
                    """
            );
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conv_player_session ON conversations(player_name, session_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conv_timestamp ON conversations(timestamp)");

            // Mod admins
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS mod_admins (
                        id IDENTITY PRIMARY KEY,
                        player_name VARCHAR(255) NOT NULL UNIQUE,
                        permission_level INT NOT NULL DEFAULT 2,
                        granted_by VARCHAR(255),
                        granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        is_active BOOLEAN DEFAULT TRUE,
                        notes CLOB
                    )
                    """
            );

            // Player locations
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS player_locations (
                        id IDENTITY PRIMARY KEY,
                        player_name VARCHAR(255) NOT NULL,
                        location_name VARCHAR(255) NOT NULL,
                        world VARCHAR(255) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        description CLOB,
                        saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(player_name, location_name)
                    )
                    """
            );

            // AI chat sessions
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS ai_chat_sessions (
                        player_name VARCHAR(255) PRIMARY KEY,
                        in_chat BOOLEAN NOT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """
            );

            // Player profiles (aggregated per player)
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        uuid VARCHAR(64) PRIMARY KEY,
                        player_name VARCHAR(255) NOT NULL,
                        first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_ip VARCHAR(128),
                        last_locale VARCHAR(32),
                        total_joins INT DEFAULT 0
                    )
                    """
            );
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_profiles_name ON player_profiles(player_name)");

            // Player connection history (append-only)
            s.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS player_connections (
                        id IDENTITY PRIMARY KEY,
                        uuid VARCHAR(64) NOT NULL,
                        player_name VARCHAR(255) NOT NULL,
                        ip VARCHAR(128),
                        locale VARCHAR(32),
                        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """
            );
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_conn_uuid_time ON player_connections(uuid, joined_at)");
        }
    }
}
