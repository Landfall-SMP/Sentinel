package com.confect1on.sentinel.db;

import com.confect1on.sentinel.config.SentinelConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(SentinelConfig.MySQL config, Logger logger) {
        this.logger = logger;

        HikariConfig hikari = new HikariConfig();
        hikari.setDriverClassName("com.confect1on.sentinel.lib.mysql.jdbc.Driver");
        hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", config.host, config.port, config.database));
        hikari.setUsername(config.username);
        hikari.setPassword(config.password);
        hikari.setMaximumPoolSize(5);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(5000);
        hikari.setIdleTimeout(60000);
        hikari.setMaxLifetime(300000);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            this.dataSource = new HikariDataSource(hikari);
            // Test connection right away
            try (Connection ignored = dataSource.getConnection()) {
                logger.info("✅ Sentinel successfully connected to MySQL at {}:{}", config.host, config.port);
            }
        } catch (Exception e) {
            logger.error("❌ Failed to connect to MySQL at {}:{} — shutting down Sentinel", config.host, config.port, e);
            throw new RuntimeException("Database connection failed", e);
        }

        initTables();
    }

    private void initTables() {
        String createLinked = """
            CREATE TABLE IF NOT EXISTS linked_accounts (
                uuid VARCHAR(36) PRIMARY KEY,
                discord_id VARCHAR(32) NOT NULL
            );
        """;

        String createPending = """
            CREATE TABLE IF NOT EXISTS pending_links (
                uuid VARCHAR(36) PRIMARY KEY,
                code VARCHAR(16) NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createLinked);
            stmt.executeUpdate(createPending);
        } catch (SQLException e) {
            logger.error("Failed to initialize database tables", e);
        }
    }

    public boolean isLinked(UUID uuid) {
        String query = "SELECT 1 FROM linked_accounts WHERE uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            logger.error("Failed to check linked status for UUID: " + uuid, e);
            return false;
        }
    }

    public void savePendingCode(UUID uuid, String code) {
        String query = """
            INSERT INTO pending_links (uuid, code, created_at)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE code = VALUES(code), created_at = VALUES(created_at)
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, code);
            stmt.setTimestamp(3, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to store pending link for UUID: " + uuid, e);
        }
    }

    public void close() {
        dataSource.close();
    }
}
