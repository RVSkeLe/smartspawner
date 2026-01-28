package github.nighter.smartspawner.spawner.data.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import github.nighter.smartspawner.SmartSpawner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pool.
 * Supports MariaDB for spawner data storage.
 */
public class DatabaseManager {
    private final SmartSpawner plugin;
    private final Logger logger;
    private HikariDataSource dataSource;

    // Configuration values
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String serverName;

    // Pool settings
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long maxLifetime;

    private static final String CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS smart_spawners (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                spawner_id VARCHAR(64) NOT NULL,
                server_name VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing)
                world_name VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,

                -- Entity data
                entity_type VARCHAR(64) NOT NULL,
                item_spawner_material VARCHAR(64) DEFAULT NULL,

                -- Settings
                spawner_exp INT NOT NULL DEFAULT 0,
                spawner_active BOOLEAN NOT NULL DEFAULT TRUE,
                spawner_range INT NOT NULL DEFAULT 16,
                spawner_stop BOOLEAN NOT NULL DEFAULT TRUE,
                spawn_delay BIGINT NOT NULL DEFAULT 500,
                max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                max_stored_exp INT NOT NULL DEFAULT 1000,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                is_at_capacity BOOLEAN NOT NULL DEFAULT FALSE,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Inventory (JSON blob)
                inventory_data MEDIUMTEXT DEFAULT NULL,

                -- Timestamps
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                -- Indexes
                UNIQUE KEY uk_server_spawner (server_name, spawner_id),
                UNIQUE KEY uk_location (server_name, world_name, loc_x, loc_y, loc_z),
                INDEX idx_server (server_name),
                INDEX idx_world (server_name, world_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    public DatabaseManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Load configuration
        this.host = plugin.getConfig().getString("database.standalone.host", "localhost");
        this.port = plugin.getConfig().getInt("database.standalone.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "smartspawner");
        this.username = plugin.getConfig().getString("database.standalone.username", "root");
        this.password = plugin.getConfig().getString("database.standalone.password", "");
        this.serverName = plugin.getConfig().getString("database.server_name", "server1");

        // Pool settings
        this.maxPoolSize = plugin.getConfig().getInt("database.standalone.pool.maximum-size", 10);
        this.minIdle = plugin.getConfig().getInt("database.standalone.pool.minimum-idle", 2);
        this.connectionTimeout = plugin.getConfig().getLong("database.standalone.pool.connection-timeout", 10000);
        this.maxLifetime = plugin.getConfig().getLong("database.standalone.pool.max-lifetime", 1800000);
    }

    /**
     * Initialize the database connection pool and create tables.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            setupDataSource();
            createTables();
            logger.info("Database connection pool initialized successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection pool", e);
            return false;
        }
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        // JDBC URL for MariaDB
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        // Pool settings
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);

        // Performance settings
        config.setPoolName("SmartSpawner-HikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        dataSource = new HikariDataSource(config);
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
            plugin.debug("Database tables created/verified successfully.");
        }
    }

    /**
     * Get a connection from the pool.
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Get the configured server name for this server.
     * @return The server name used to identify spawners
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Check if the database connection pool is active.
     * @return true if the pool is active and accepting connections
     */
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Shutdown the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
